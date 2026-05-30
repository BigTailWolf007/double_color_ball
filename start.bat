@echo off
chcp 65001 >nul
title 双色球分析系统

set BASE_DIR=%~dp0
set BACKEND_DIR=%BASE_DIR%dcb-backend
set FRONTEND_DIR=%BASE_DIR%dcb-frontend\app
set BACKEND_PORT=8080
set FRONTEND_PORT=8081

echo ============================================
echo   双色球分析系统 一键启动
echo ============================================
echo.

:: -------- 检查 Java --------
where java >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 java，请先安装 JDK 并配置 PATH
    pause
    exit /b 1
)

:: -------- 检查 Maven --------
where mvn >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 mvn，请先安装 Maven 并配置 PATH
    pause
    exit /b 1
)

:: -------- 检查 Node / npx --------
where npx >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 npx，请先安装 Node.js 并配置 PATH
    pause
    exit /b 1
)

:: -------- 编译打包后端 --------
echo [1/3] 正在编译后端...
cd /d "%BACKEND_DIR%"
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [错误] 后端编译失败，请检查代码
    pause
    exit /b 1
)
echo [1/3] 后端编译完成

:: -------- 找到 jar 包 --------
for /f "delims=" %%f in ('dir /b /s "%BACKEND_DIR%\target\*.jar" 2^>nul ^| findstr /v "original"') do set JAR_FILE=%%f
if not defined JAR_FILE (
    echo [错误] 未找到后端 jar 包
    pause
    exit /b 1
)

:: -------- 启动后端 --------
echo [2/3] 正在启动后端服务（端口 %BACKEND_PORT%）...
start "后端服务 :%BACKEND_PORT%" cmd /k "java -jar "%JAR_FILE%" && pause"
echo [2/3] 后端服务已启动，等待就绪...

:: 等待后端启动（最多 30 秒，检测端口是否监听）
set /a WAIT=0
:WAIT_BACKEND
timeout /t 2 /nobreak >nul
set /a WAIT+=2
powershell -Command "if ((Test-NetConnection -ComputerName localhost -Port %BACKEND_PORT% -InformationLevel Quiet -WarningAction SilentlyContinue)) { exit 0 } else { exit 1 }" >nul 2>&1
if not errorlevel 1 goto BACKEND_READY
if %WAIT% geq 30 (
    echo [警告] 后端启动超时，继续启动前端...
    goto START_FRONTEND
)
goto WAIT_BACKEND

:BACKEND_READY
echo [2/3] 后端服务就绪

:: -------- 启动前端 --------
:START_FRONTEND
echo [3/3] 正在启动前端服务（端口 %FRONTEND_PORT%）...
start "前端服务 :%FRONTEND_PORT%" cmd /k "npx serve -p %FRONTEND_PORT% "%FRONTEND_DIR%" && pause"
echo [3/3] 前端服务已启动

:: -------- 等待前端端口就绪后打开浏览器 --------
timeout /t 3 /nobreak >nul
start "" "http://localhost:%FRONTEND_PORT%"

echo.
echo ============================================
echo   启动完成
echo   前端地址：http://localhost:%FRONTEND_PORT%
echo   后端地址：http://localhost:%BACKEND_PORT%
echo   关闭对应窗口即可停止对应服务
echo ============================================
echo.
pause
