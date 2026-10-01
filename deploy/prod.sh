#!/bin/bash
SERVER_DEPLOY=82.165.247.220
TARGET_DIR=/opt/deploy/inhouse
APP_ID=inhouse-media-api
PACKAGE_NAME=com.qrcode.media

echo "Build source..."
cd ../source/qrcode-media
mvn clean package
cd ../../deploy

echo "Update config..."
mkdir release

cp ../source/qrcode-media/target/qrcode-media-spring-boot.jar release/app.jar

cp config/* release/
rm -rf release/application-dev.properties
sed -i '' "s/{ENV}/prod/g" release/application.properties
sed -i '' "s/{APP_ID}/$APP_ID/g" release/application-prod.properties
sed -i '' "s/{PACKAGE_NAME}/$PACKAGE_NAME/g" release/application-prod.properties
sed -i '' "s/{PACKAGE_NAME}/$PACKAGE_NAME/g" release/logback-spring.xml

cp service-template.service release/$APP_ID.service
sed -i '' "s/{CONFIG_LOCATION}/$(printf '%s\n' "$TARGET_DIR" | sed -e 's/[]\/$*.^[]/\\&/g')/g" release/$APP_ID.service
sed -i '' "s/{ENV}/prod/g" release/$APP_ID.service

echo "Compress source..."
gtar -czf api.tar.gz release

echo "Deploy to server..."
echo " ---> Stop old service..."
ssh root@$SERVER_DEPLOY "mkdir -p $TARGET_DIR"
ssh root@$SERVER_DEPLOY "systemctl stop $APP_ID.service"
ssh root@$SERVER_DEPLOY "rm -rf $TARGET_DIR/*"

echo " ---> Remove old service"
ssh root@$SERVER_DEPLOY "rm -rf /lib/systemd/system/$APP_ID.service"

echo " ---> Upload build..."
scp api.tar.gz root@$SERVER_DEPLOY:$TARGET_DIR/api.tar.gz
ssh root@$SERVER_DEPLOY "cd $TARGET_DIR && tar -xzf api.tar.gz && rm -rf api.tar.gz && mv release/* . && rm -rf release"
ssh root@$SERVER_DEPLOY "cd $TARGET_DIR && rm -rf config.properties && cp ../api_application.prop config.properties"

echo " ---> Deploy new service..."
ssh root@$SERVER_DEPLOY "mv $TARGET_DIR/$APP_ID.service /lib/systemd/system/$APP_ID.service"
ssh root@$SERVER_DEPLOY "chmod 644 /lib/systemd/system/$APP_ID.service && systemctl daemon-reload"
ssh root@$SERVER_DEPLOY "systemctl enable $APP_ID.service"
ssh root@$SERVER_DEPLOY "systemctl start $APP_ID.service"

echo "Cleanup..."
rm -rf release
rm -rf api.tar.gz
echo "############# DONE #############"