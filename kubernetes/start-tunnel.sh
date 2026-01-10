#!/bin/bash

echo "🚇 Starting Minikube Tunnel for WSL2..."
echo ""
echo "This script will:"
echo "  1. Add thesisapp.local to WSL2 /etc/hosts"
echo "  2. Add thesisapp.local to Windows hosts file"
echo "  3. Start minikube tunnel"
echo ""
echo "After tunnel starts, access from Windows browser:"
echo "  📱 Frontend:      http://thesisapp.local"
echo "  🔧 Backend API:   http://thesisapp.local/api"
echo "  📧 MailHog:       http://mailhog.thesisapp.local"
echo "  💾 MinIO Console: http://minio.thesisapp.local"
echo ""
echo "⚠️  Keep this terminal open - tunnel will stop if you close it!"
echo "   Press Ctrl+C to stop the tunnel."
echo ""

# Get Minikube IP
MINIKUBE_IP=$(minikube ip 2>/dev/null)

if [ -z "$MINIKUBE_IP" ]; then
    echo "❌ Error: Minikube is not running!"
    echo "   Start it with: minikube start"
    exit 1
fi

echo "✅ Minikube is running at $MINIKUBE_IP"
echo ""

# -----------------------------
# Add to WSL2 /etc/hosts
# -----------------------------
if ! grep -q "thesisapp.local" /etc/hosts 2>/dev/null; then
    echo "📝 Adding thesisapp.local to WSL2 /etc/hosts..."
    echo "$MINIKUBE_IP thesisapp.local mailhog.thesisapp.local minio.thesisapp.local" | sudo tee -a /etc/hosts > /dev/null
    echo "✅ Added to WSL2 /etc/hosts"
else
    echo "✅ thesisapp.local already in WSL2 /etc/hosts"
fi

# -----------------------------
# Add to Windows hosts file
# -----------------------------
WINDOWS_HOSTS="/mnt/c/Windows/System32/drivers/etc/hosts"

if [ -f "$WINDOWS_HOSTS" ]; then
    if ! grep -q "thesisapp.local" "$WINDOWS_HOSTS" 2>/dev/null; then
        echo ""
        echo "📝 Adding thesisapp.local to Windows hosts file..."
        echo "   This requires Administrator privileges in Windows."
        echo ""
        echo "   OPTION 1: Let this script try (may fail):"
        echo "   We'll attempt to add it via PowerShell..."

        # Try to add via PowerShell as Administrator
        # Note: This often fails due to UAC restrictions, manual method is more reliable
        powershell.exe -Command "Start-Process powershell -Verb RunAs -ArgumentList '-NoProfile -Command \"Add-Content C:\\Windows\\System32\\drivers\\etc\\hosts -Value ''$MINIKUBE_IP thesisapp.local mailhog.thesisapp.local minio.thesisapp.local''\"'" 2>/dev/null || true

        sleep 2

        if grep -q "thesisapp.local" "$WINDOWS_HOSTS" 2>/dev/null; then
            echo "   ✅ Added to Windows hosts file!"
        else
            echo "   ⚠️  Automatic addition failed. Please add manually:"
            echo ""
            echo "   OPTION 2: Manual Steps (Recommended):"
            echo "   1. Open Notepad as Administrator (Windows search → Notepad → Right-click → Run as Administrator)"
            echo "   2. File → Open → C:\\Windows\\System32\\drivers\\etc\\hosts"
            echo "   3. Add this line at the end:"
            echo "      $MINIKUBE_IP thesisapp.local mailhog.thesisapp.local minio.thesisapp.local"
            echo "   4. Save and close"
            echo ""
            echo "Press ENTER when you've added it manually, or ENTER to continue anyway..."
            read -r
        fi
    else
        echo "✅ thesisapp.local already in Windows hosts file"
    fi
else
    echo "⚠️  Windows hosts file not found at $WINDOWS_HOSTS"
    echo "   Add manually: $MINIKUBE_IP thesisapp.local to C:\\Windows\\System32\\drivers\\etc\\hosts"
fi

# -----------------------------
# Configure passwordless sudo for minikube tunnel (optional)
# -----------------------------
SUDOERS_FILE="/etc/sudoers.d/minikube-tunnel"
MINIKUBE_PATH=$(which minikube)

if [ ! -f "$SUDOERS_FILE" ]; then
    echo ""
    echo "🔐 Setting up passwordless sudo for minikube tunnel..."
    echo "   (This only allows 'minikube tunnel' - safe and convenient)"

    echo "$USER ALL=(ALL) NOPASSWD: $MINIKUBE_PATH tunnel" | sudo tee "$SUDOERS_FILE" > /dev/null
    sudo chmod 0440 "$SUDOERS_FILE"

    echo "✅ Passwordless sudo configured!"
fi

echo ""
echo "🚇 Starting tunnel..."
echo "   Keep this terminal open!"
echo ""

# Start tunnel (blocks until Ctrl+C)
sudo HOME="$HOME" minikube tunnel --profile minikube

# Cleanup on exit
echo ""
echo "🛑 Tunnel stopped."
