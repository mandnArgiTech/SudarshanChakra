-- Apply on existing DBs that were initialized before simulator seed data existed.
-- Safe to re-run: uses ON CONFLICT DO NOTHING.

INSERT INTO cameras (id, node_id, name, rtsp_url) VALUES
    ('cam-01', 'edge-node-a', 'Front Gate', 'rtsp://127.0.0.1/placeholder/cam-01'),
    ('cam-02', 'edge-node-a', 'Livestock Pen', 'rtsp://127.0.0.1/placeholder/cam-02'),
    ('cam-03', 'edge-node-a', 'Pond Safety', 'rtsp://127.0.0.1/placeholder/cam-03'),
    ('cam-04', 'edge-node-a', 'Walkway', 'rtsp://127.0.0.1/placeholder/cam-04'),
    ('cam-05', 'edge-node-a', 'Storage', 'rtsp://127.0.0.1/placeholder/cam-05'),
    ('cam-06', 'edge-node-a', 'Equipment Shed', 'rtsp://127.0.0.1/placeholder/cam-06'),
    ('cam-07', 'edge-node-a', 'North Field', 'rtsp://127.0.0.1/placeholder/cam-07'),
    ('esp32', 'edge-node-a', 'ESP32 Pond Cam', 'rtsp://127.0.0.1/placeholder/esp32')
ON CONFLICT (id) DO NOTHING;

INSERT INTO zones (id, camera_id, name, zone_type, priority, target_classes, polygon) VALUES
    ('storage_perimeter', 'cam-05', 'Storage Perimeter', 'zero_tolerance', 'critical', ARRAY['snake'], '[[0,0],[100,0],[100,100],[0,100]]'::jsonb),
    ('pond_safety', 'cam-03', 'Pond Safety', 'zero_tolerance', 'critical', ARRAY['child','fall_detected'], '[[0,0],[100,0],[100,100],[0,100]]'::jsonb),
    ('equipment_shed', 'cam-06', 'Equipment Shed', 'hazard', 'critical', ARRAY['fire'], '[[0,0],[100,0],[100,100],[0,100]]'::jsonb),
    ('front_gate', 'cam-01', 'Front Gate', 'intrusion', 'high', ARRAY['person'], '[[0,0],[100,0],[100,100],[0,100]]'::jsonb),
    ('walkway_hazard', 'cam-04', 'Walkway Hazard', 'hazard', 'high', ARRAY['scorpion'], '[[0,0],[100,0],[100,100],[0,100]]'::jsonb),
    ('livestock_pen', 'cam-02', 'Livestock Pen', 'livestock_containment', 'warning', ARRAY['cow'], '[[0,0],[100,0],[100,100],[0,100]]'::jsonb),
    ('north_field', 'cam-07', 'North Field', 'hazard', 'high', ARRAY['smoke'], '[[0,0],[100,0],[100,100],[0,100]]'::jsonb)
ON CONFLICT (id) DO NOTHING;
