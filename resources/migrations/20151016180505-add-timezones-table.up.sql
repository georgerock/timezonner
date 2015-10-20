CREATE TABLE timezones
(id INTEGER PRIMARY KEY,
 name VARCHAR(30),
 city VARCHAR(30),
 offset INTEGER,
 addedby INTEGER,
 FOREIGN KEY(addedby) REFERENCES user(id));