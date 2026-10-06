-- Manual development-only reset. Verify the target database is:
-- jdbc:postgresql://localhost:5432/shareway-booking-pricing
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
