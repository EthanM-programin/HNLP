-- V4: lower floor capacity to 4 shoppers so the queue (US-1.3) can be demoed with the 5 personas.
-- The 5th check-in goes to WAITING and is promoted automatically when someone checks out or times out.
-- Change it any time without a migration: UPDATE app_setting SET setting_value = '<n>' WHERE setting_key = 'max_floor_capacity';
UPDATE app_setting SET setting_value = '4' WHERE setting_key = 'max_floor_capacity';
