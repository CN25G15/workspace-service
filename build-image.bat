@ECHO OFF
CD %~dp0
CALL mvnw.cmd clean package -Pnative -DskipTests --define quarkus.native.container-build=true --define quarkus.container-image.build=true
docker build -f src\main\docker\Dockerfile.jvm -t tripmonkey/workspace-service .