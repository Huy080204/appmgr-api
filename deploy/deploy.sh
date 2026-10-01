#!/bin/bash
SERVER_DEPLOY=192.168.155.10
TARGET_DIR=/deploy/media
APP_ID=media-api
PACKAGE_NAME=com.media.api
PROJECT_DIR="../source/media-api"
JAR_NAME="media-api.jar"

echo "Build source..."
cd "$PROJECT_DIR" || { echo "Folder not found: $PROJECT_DIR"; exit 1; }
mvn clean package dependency:copy-dependencies -DoutputDirectory=target/lib -DincludeScope=runtime || { echo "Build failed"; exit 1; }

cd ../../deploy || exit 1
echo "Update config..."
# delete release folder if exist
rm -rf release
mkdir release

# copy file jar and folder lib
cp "$PROJECT_DIR/target/$JAR_NAME" release/
cp -r "$PROJECT_DIR/target/lib" release/

echo "Deploy to server..."
# copy to server
scp -r release/* root@"$SERVER_DEPLOY":"$TARGET_DIR" || { echo "SCP failed"; exit 1; }

echo " ---> Restart service..."
ssh root@"$SERVER_DEPLOY" "systemctl restart $APP_ID.service && echo 'Service restarted successfully' || echo 'Failed to restart service'"
