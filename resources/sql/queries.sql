-- name: create-user!
-- creates a new user record
INSERT INTO users
(name, email, pass, isadmin)
VALUES (:name, :email, :pass, :isadmin)

-- name: update-user!
-- update an existing user record
UPDATE users
SET name = :name, email = :email
WHERE id = :id

-- name: get-user
-- retrieve a user given the id.
SELECT * FROM users
WHERE id = :id

-- name: get-user-by-email
-- retrieve a user given the id.
SELECT * FROM users
WHERE email = :email

-- name: get-users
-- retrieves all users
SELECT * FROM users

-- name: delete-user!
-- delete a user given the id
DELETE FROM users
WHERE id = :id

-- name: create-timezone!
-- creates a new timezone record
INSERT INTO timezones
(name, city, offset, addedby)
VALUES (:name, :city, :offset, :addedby)

-- name: update-timezone!
-- update an existing timezone record
UPDATE timezones
SET name = :name, city = :city, offset = :offset
WHERE id = :id

-- name: get-timezone
-- retrieve a timezone given the id.
SELECT * FROM timezones
WHERE id = :id

-- name: get-timezones
-- retrieves all timezones.
SELECT * FROM timezones

-- name: get-timezones-by-user
-- retrieves all timezones for a specific user.
SELECT * FROM timezones WHERE addedby = :addedby

-- name: delete-timezone!
-- delete a timezone given the id
DELETE FROM timezones
WHERE id = :id