INSERT INTO settings (setting_key, setting_value)
SELECT 'app.name', 'NMX SaaS Tenant'
WHERE NOT EXISTS (SELECT 1 FROM settings WHERE setting_key = 'app.name');
