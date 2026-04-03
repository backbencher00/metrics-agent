#!/bin/bash

set -e  # stop on error

echo "Installing Metrics Agent..."

JAR_URL="https://github.com/backbencher00/metrics-agent/releases/download/V.1/Metrics-tracking-agent-1.0-SNAPSHOT.jar"
OS="$(uname)"

if [[ "$OS" == "Darwin" ]]; then
    echo "Detected macOS"

    INSTALL_DIR="$HOME/metrics-agent"
    mkdir -p "$INSTALL_DIR"

    echo "Downloading agent..."
    curl -f -L -o "$INSTALL_DIR/agent.jar" "$JAR_URL"

    if [ ! -s "$INSTALL_DIR/agent.jar" ]; then
        echo "❌ Download failed"
        exit 1
    fi

    echo "Starting agent..."
    nohup java -jar "$INSTALL_DIR/agent.jar" > "$INSTALL_DIR/logs.txt" 2>&1 &

    echo "✅ Agent running on macOS"
    exit 0
fi

if [[ "$OS" == "Linux" ]]; then
    echo "Detected Linux"

    INSTALL_DIR="/opt/metrics-agent"
    sudo mkdir -p "$INSTALL_DIR"

    echo "Downloading agent..."
    sudo curl -f -L -o "$INSTALL_DIR/agent.jar" "$JAR_URL"

    if [ ! -s "$INSTALL_DIR/agent.jar" ]; then
        echo "❌ Download failed"
        exit 1
    fi

    # Check if systemd exists
    if command -v systemctl &> /dev/null; then
        echo "Using systemd"

        sudo tee /etc/systemd/system/metrics-agent.service > /dev/null <<EOF
[Unit]
Description=Metrics Agent
After=network.target

[Service]
ExecStart=/usr/bin/java -jar $INSTALL_DIR/agent.jar
Restart=always

[Install]
WantedBy=multi-user.target
EOF

        sudo systemctl daemon-reload
        sudo systemctl enable metrics-agent
        sudo systemctl start metrics-agent

        echo "✅ Agent installed as service"

    else
        echo "No systemd found → running manually"

        nohup java -jar "$INSTALL_DIR/agent.jar" > "$INSTALL_DIR/logs.txt" 2>&1 &

        echo "✅ Agent running (nohup mode)"
    fi

    exit 0
fi

echo "❌ Unsupported OS"
exit 1