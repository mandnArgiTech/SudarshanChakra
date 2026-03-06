FROM ultralytics/ultralytics:latest
WORKDIR /app

RUN apt-get update && apt-get install -y \
    libgl1-mesa-glx \
    libglib2.0-0 \
    openvpn \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .
# Expose port 5000 for the Local Edge Web GUI Polygon drawer
EXPOSE 5000 
CMD ["python", "farm_edge_node.py"]
