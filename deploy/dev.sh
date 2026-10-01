#!/bin/bash
SERVER_DEPLOY=192.168.102.7
TARGET_DIR=/opt/deploy/base_project/itz-api
APP_ID=base-project
PACKAGE_NAME=com.base.api
PORT=1000

echo "Build source..."
cd ../source/base-project
mvn clean package dependency:copy-dependencies -DoutputDirectory=target/lib -DincludeScope=runtime
cd ../../deploy

echo "Update config..."
# delete release folder if exist
rm -rf release
mkdir release

cp ../source/base-project/target/base-project-0.0.1.jar release/app.jar
cp -r ../source/base-project/target/lib release/lib

cp config/* release/
rm -rf release/application-prod.properties

sed -i "s/{ENV}/dev/g" release/application.properties
sed -i "s/{PORT}/$PORT/g" release/application-dev.properties
sed -i "s/{APP_ID}/$APP_ID/g" release/application-dev.properties
sed -i "s/{PACKAGE_NAME}/$PACKAGE_NAME/g" release/application-dev.properties
sed -i "s/{PACKAGE_NAME}/$PACKAGE_NAME/g" release/logback-spring.xml

cp service-template.service release/$APP_ID.service
sed -i "s/{CONFIG_LOCATION}/$(printf '%s\n' "$TARGET_DIR" | sed -e 's/[]\/$*.^[]/\\&/g')/g" release/$APP_ID.service
sed -i "s/{ENV}/dev/g" release/$APP_ID.service
sed -i "s/{APP_ID}/$APP_ID/g" release/$APP_ID.service

cp logs-template.conf release/$APP_ID.conf
sed -i "s/{APP_ID}/$APP_ID/g" release/$APP_ID.conf

echo "Compress source..."
tar -czf api.tar.gz release
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

echo " ---> Deploy new service..."
ssh root@$SERVER_DEPLOY "mv $TARGET_DIR/$APP_ID.service /lib/systemd/system/$APP_ID.service"
ssh root@$SERVER_DEPLOY "chmod 644 /lib/systemd/system/$APP_ID.service && systemctl daemon-reload"
ssh root@$SERVER_DEPLOY "systemctl enable $APP_ID.service"

echo " ---> Deploy log service..."
# Create log directory
ssh root@$SERVER_DEPLOY "mkdir -p /var/log/$APP_ID/ && touch /var/log/$APP_ID/$APP_ID.json"
# Deploy log service
ssh root@$SERVER_DEPLOY "rm -rf /etc/rsyslog.d/$APP_ID.conf && cd $TARGET_DIR && cp $APP_ID.conf /etc/rsyslog.d/$APP_ID.conf && systemctl restart rsyslog"

# Start application
ssh root@$SERVER_DEPLOY "systemctl start $APP_ID.service"

echo "Cleanup..."
rm -rf release
rm -rf api.tar.gz
echo "############# DONE #############"