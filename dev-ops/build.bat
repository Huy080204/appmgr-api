@echo off
echo ==== Step 1: Build Spring Boot project ====
cd ../source/com-master-api
call mvn clean package -DskipTests

echo.
echo ==== Step 2: Copy JAR to dev-ops ====
copy target\master-api-0.0.1.jar ..\..\dev-ops\

echo.
echo ==== Step 3: Build Docker image ====
cd ..\..\dev-ops
docker build -t huypham0802/master-api-service:latest .

echo.
echo ==== DONE! Docker image created: huypham0802/master-api-service:latest ====
