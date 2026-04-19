USE isp_onboarding;

INSERT INTO companies (company_name, email, phone, pic_name, status)
VALUES (
  'PT Fiber Awan Digital',
  'hello@fiberawan.co.id',
  '+6281234567890',
  'Rizky Pratama',
  'trial'
);

INSERT INTO users (company_id, name, username, password_hash, role, is_active)
VALUES (
  1,
  'Rizky Pratama',
  'fiberawanowner',
  '$2b$10$tr0vgkwok07lxUGyrgkbLOK.oJeM/qpdpliYsTHEZjr5q8MHX9xsS',
  'owner',
  1
);
