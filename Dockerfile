FROM gradle:7.5.1-jdk11 AS build
ARG release_version
ARG nexus_url
ARG nexus_user
ARG nexus_password

COPY ./ .
RUN gradle --no-daemon clean build publish \
    -Prelease_version=${release_version} \
    -Pnexus_url=${nexus_url} \
    -Pnexus_user=${nexus_user} \
    -Pnexus_password=${nexus_password}
