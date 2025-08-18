#!/bin/bash
set -e

echo "Starting ArcGIS configuration phase..."

# Wait for services to stabilize
sleep 30

# Check service status
sudo systemctl status arcgisserver || echo "Service status check completed"

# Navigate to cookbook directory
cd /root/arcgis-cookbook

# Run the configuration phase
echo "Running cinc-client configuration..."
sudo cinc-client -z -j cookbooks/base2-arcgis/templates/default/arcgis-enterprise-base/11.5/linux/arcgis-enterprise-primary.json

echo "ArcGIS configuration phase completed"
