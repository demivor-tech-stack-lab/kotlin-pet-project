-- =====================================================================
-- V2: Du lieu THAM CHIEU (reference data)
--
-- Phan biet ro hai loai du lieu khoi tao:
--   - Reference data (o day): production CUNG CAN. Loai xe la mot phan cua
--     nghiep vu, thieu no thi khong them xe duoc. => dat trong migration.
--   - Sample data (DataSeeder.kt): chi de nghich luc hoc. Co tai khoan
--     admin/123456 nen TUYET DOI khong duoc chay o production
--     => nam trong code va bi chan boi SEED_DATA=false.
-- =====================================================================

INSERT INTO vehicle_types (name, seats, description) VALUES
    ('Xe may',     2, 'Xe so hoac tay ga'),
    ('O to 4 cho', 4, 'Sedan hoac Hatchback'),
    ('O to 7 cho', 7, 'SUV hoac MPV gia dinh');
