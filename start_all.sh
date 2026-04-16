#!/usr/bin/env bash
# ==============================================================================
# ShopNova - One-Click Start Script
# This script will build all microservices via Maven and spin them up concurrently 
# alongside the UI dev proxy server.
# 
# Usage: ./start_all.sh
# ==============================================================================

# Ensure script halts on failure
set -e

echo "=========================================="
echo "🚀 Building ShopNova Backend (Maven)..."
echo "=========================================="
cd backend
mvn clean install -DskipTests
cd ..

echo ""
echo "=========================================="
echo "⚡ Starting Microservices concurrently..."
echo "=========================================="

# Array to store process IDs
pids=()

# Function to start a service
start_service() {
    local dir=$1
    local name=$2
    
    echo "Starting $name..."
    cd "backend/$dir"
    mvn spring-boot:run > "../../${name}.log" 2>&1 &
    pids+=($!)
    cd ../..
    echo "[$!] $name is booting (Logs: ${name}.log)"
}

start_service "product-service" "product"
start_service "cart-service" "cart"
start_service "order-service" "order"
start_service "payment-service" "payment"
start_service "user-service" "user"

# Wait a few seconds for services to establish connections before starting gateway
echo "Waiting 5 seconds before starting API gateway..."
sleep 5
start_service "api-gateway" "gateway"

echo ""
echo "=========================================="
echo "🌐 Starting UI Proxy Server..."
echo "=========================================="
# Runs the python frontend in foreground, effectively holding the terminal open 
# and tying it to the user session
echo "Frontend dev server logging to terminal! Access the app at http://127.0.0.1:4174"
echo "Press Ctrl+C to stop ALL services."
echo "=========================================="

# trap ctrl-c and call clean_up()
trap clean_up INT

clean_up() {
    echo ""
    echo "🛑 Stopping all ShopNova services..."
    for pid in "${pids[@]}"; do
        kill "$pid" 2>/dev/null || true
    done
    echo "Done! All background services terminated."
    exit 0
}

# Run the python server directly in the foreground
python3 dev_server.py --port 4174
