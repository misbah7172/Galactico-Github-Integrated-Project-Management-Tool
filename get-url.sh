#!/bin/bash

echo "🔍 Getting Cloudflare Tunnel URL for AutoTrack"
echo "============================================="

# Check if container is running
if [ ! "$(docker ps -q -f name=autotrack-app)" ]; then
    echo "❌ AutoTrack container is not running!"
    echo "💡 Start it with: docker-compose up --build"
    exit 1
fi

echo "📋 Fetching tunnel URL from container logs..."
echo ""

# Get the logs and filter for the tunnel URL
TUNNEL_URL=$(docker-compose logs autotrack-app 2>&1 | grep -oE 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' | tail -1)

if [ -n "$TUNNEL_URL" ]; then
    echo "✅ Found your Cloudflare tunnel URL:"
    echo "🌐 $TUNNEL_URL"
    echo ""
    echo "📝 Don't forget to update your GitHub OAuth App with this URL:"
    echo "   Authorization callback URL: $TUNNEL_URL/login/oauth2/code/github"
    echo ""
    echo "🔗 Quick links:"
    echo "   🏠 Home: $TUNNEL_URL"
    echo "   ❤️  Health: $TUNNEL_URL/actuator/health"
    echo "   📊 Local: https://brings-broadcasting-tucson-fioricet.trycloudflare.com"
else
    echo "⏳ Tunnel URL not found yet. Container might still be starting..."
    echo "📋 Showing recent logs:"
    echo ""
    docker-compose logs --tail=20 autotrack-app
    echo ""
    echo "💡 Try running this script again in a few seconds."
fi