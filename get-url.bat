@echo off
echo 🔍 Getting Cloudflare Tunnel URL for AutoTrack
echo =============================================

REM Check if container is running
docker ps -q -f name=autotrack-app >nul 2>&1
if errorlevel 1 (
    echo ❌ AutoTrack container is not running!
    echo 💡 Start it with: docker-compose up --build
    pause
    exit /b 1
)

echo 📋 Fetching tunnel URL from container logs...
echo.

REM Get the logs and look for tunnel URL
docker-compose logs autotrack-app | findstr "trycloudflare.com" > temp_url.txt 2>nul

if exist temp_url.txt (
    for /f "tokens=*" %%i in (temp_url.txt) do set LAST_LINE=%%i
    del temp_url.txt
    
    if defined LAST_LINE (
        echo ✅ Found your Cloudflare tunnel URL in logs!
        echo 🌐 Check the logs above for the tunnel URL
        echo.
        echo 📝 Don't forget to update your GitHub OAuth App with the tunnel URL:
        echo    Authorization callback URL: [TUNNEL_URL]/login/oauth2/code/github
        echo.
        echo 🔗 Quick access:
        echo    📊 Local: https://brings-broadcasting-tucson-fioricet.trycloudflare.com
        echo    ❤️  Health: https://brings-broadcasting-tucson-fioricet.trycloudflare.com/actuator/health
    ) else (
        echo ⏳ Tunnel URL not found yet. Container might still be starting...
        echo 📋 Showing recent logs:
        echo.
        docker-compose logs --tail=20 autotrack-app
        echo.
        echo 💡 Try running this script again in a few seconds.
    )
) else (
    echo ⏳ Tunnel URL not found yet. Container might still be starting...
    echo 📋 Showing recent logs:
    echo.
    docker-compose logs --tail=20 autotrack-app
    echo.
    echo 💡 Try running this script again in a few seconds.
)

pause