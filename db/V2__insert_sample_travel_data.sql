-- V2__insert_sample_travel_data.sql  (all monetary values in INR)
INSERT INTO countries (country_code, country_name) VALUES
 ('IN','India'), ('TH','Thailand'), ('MY','Malaysia'), ('VN','Vietnam'), ('AE','United Arab Emirates');

INSERT INTO cities (country_id, city_name)
SELECT c.country_id, v.city FROM (VALUES
 ('IN','Kolkata'), ('IN','Delhi'), ('IN','Bangalore'), ('IN','Pune'), ('IN','Mumbai'), ('IN','Chennai'), ('IN','Bhubaneswar'),
 ('TH','Bangkok'), ('TH','Phuket'), ('TH','Phi Phi Island'),
 ('MY','Kuala Lumpur'), ('MY','Penang'),
 ('VN','Ho Chi Minh City'), ('VN','Hanoi'),
 ('AE','Dubai')) AS v(cc, city)
JOIN countries c ON c.country_code = v.cc;

INSERT INTO airports (airport_code, airport_name, city_id)
SELECT v.code, v.name, ci.city_id FROM (VALUES
 ('CCU','Netaji Subhas Chandra Bose International Airport','Kolkata'),
 ('DEL','Indira Gandhi International Airport','Delhi'),
 ('BLR','Kempegowda International Airport','Bangalore'),
 ('PNQ','Pune Airport','Pune'),
 ('BOM','Chhatrapati Shivaji Maharaj International Airport','Mumbai'),
 ('MAA','Chennai International Airport','Chennai'),
 ('BBI','Biju Patnaik International Airport','Bhubaneswar'),
 ('BKK','Suvarnabhumi Airport','Bangkok'),
 ('HKT','Phuket International Airport','Phuket'),
 ('KUL','Kuala Lumpur International Airport','Kuala Lumpur'),
 ('PEN','Penang International Airport','Penang'),
 ('SGN','Tan Son Nhat International Airport','Ho Chi Minh City'),
 ('HAN','Noi Bai International Airport','Hanoi'),
 ('DXB','Dubai International Airport','Dubai')) AS v(code, name, city)
JOIN cities ci ON ci.city_name = v.city;

INSERT INTO airlines (airline_code, airline_name) VALUES
 ('6E','IndiGo'), ('AI','Air India'), ('SG','SpiceJet'), ('TG','Thai Airways'),
 ('AK','AirAsia'), ('VJ','VietJet Air'), ('EK','Emirates'), ('FD','Thai AirAsia');

INSERT INTO flights (flight_number, airline_id, source_airport_id, destination_airport_id, flight_type, departure_time, duration_minutes)
SELECT v.fn, al.airline_id, s.airport_id, d.airport_id, v.ft, v.dt::time, v.dur
FROM (VALUES
 ('6E-201','6E','CCU','DEL','DOMESTIC','06:15',150),
 ('AI-021','AI','CCU','DEL','DOMESTIC','09:40',155),
 ('6E-512','6E','CCU','BLR','DOMESTIC','07:30',165),
 ('SG-421','SG','CCU','BLR','DOMESTIC','13:10',170),
 ('6E-714','6E','CCU','PNQ','DOMESTIC','08:05',170),
 ('AI-771','AI','CCU','BOM','DOMESTIC','10:20',175),
 ('6E-355','6E','CCU','MAA','DOMESTIC','11:45',140),
 ('6E-933','6E','CCU','BBI','DOMESTIC','15:30',60),
 ('FD-136','FD','CCU','BKK','INTERNATIONAL','09:00',150),
 ('TG-314','TG','CCU','BKK','INTERNATIONAL','12:15',145),
 ('AK-050','AK','CCU','KUL','INTERNATIONAL','23:30',230),
 ('AI-380','AI','CCU','BKK','INTERNATIONAL','18:00',150),
 ('6E-1048','6E','CCU','HKT','INTERNATIONAL','05:50',210),
 ('AK-051','AK','CCU','PEN','INTERNATIONAL','22:10',215),
 ('VJ-810','VJ','CCU','SGN','INTERNATIONAL','20:25',175),
 ('VJ-812','VJ','CCU','HAN','INTERNATIONAL','21:10',165),
 ('EK-571','EK','CCU','DXB','INTERNATIONAL','03:45',270)) AS v(fn, alc, src, dst, ft, dt, dur)
JOIN airlines al ON al.airline_code = v.alc
JOIN airports s  ON s.airport_code = v.src
JOIN airports d  ON d.airport_code = v.dst;

INSERT INTO flight_fares (flight_id, fare_class, base_fare, taxes, total_fare, departure_date, seats_available)
SELECT f.flight_id, v.cls, v.base, v.tax, v.base + v.tax, v.dd::date, v.seats
FROM (VALUES
 ('6E-201','ECONOMY',4200.00, 850.00,'2026-11-15',120),
 ('AI-021','ECONOMY',4900.00, 900.00,'2026-11-15', 80),
 ('6E-512','ECONOMY',3900.00, 780.00,'2026-11-15',110),
 ('SG-421','ECONOMY',3600.00, 760.00,'2026-11-15', 90),
 ('6E-714','ECONOMY',5200.00, 950.00,'2026-11-16',100),
 ('AI-771','ECONOMY',5600.00, 980.00,'2026-11-16', 70),
 ('6E-355','ECONOMY',3800.00, 770.00,'2026-11-16',105),
 ('6E-933','ECONOMY',2400.00, 520.00,'2026-11-16', 95),
 ('FD-136','ECONOMY',9800.00,2100.00,'2026-11-20',150),
 ('TG-314','ECONOMY',12500.00,2400.00,'2026-11-20', 60),
 ('AI-380','ECONOMY',11200.00,2300.00,'2026-12-05', 65),
 ('6E-1048','ECONOMY',11800.00,2250.00,'2026-12-05',140),
 ('AK-050','ECONOMY',10400.00,2150.00,'2027-01-10',130),
 ('AK-051','ECONOMY',10900.00,2200.00,'2027-01-10',120),
 ('VJ-810','ECONOMY',9300.00,1900.00,'2026-11-25',140),
 ('VJ-812','ECONOMY',9100.00,1850.00,'2026-11-25',130),
 ('EK-571','ECONOMY',21500.00,3600.00,'2026-12-12', 75)) AS v(fn, cls, base, tax, dd, seats)
JOIN flights f ON f.flight_number = v.fn;

INSERT INTO hotels (hotel_name, city_id, star_rating, address)
SELECT v.hn, ci.city_id, v.stars, v.addr FROM (VALUES
 ('Sukhumvit Budget Inn','Bangkok',2,'Sukhumvit Soi 11, Bangkok'),
 ('Riverside City Hotel','Bangkok',3,'Charoen Krung Rd, Bangkok'),
 ('Patong Backpackers Lodge','Phuket',2,'Patong Beach, Phuket'),
 ('Kata Sea Breeze Resort','Phuket',3,'Kata Beach, Phuket'),
 ('Tonsai Bay Bungalows','Phi Phi Island',2,'Ton Sai Bay, Phi Phi Don'),
 ('Bukit Bintang Stay','Kuala Lumpur',2,'Jalan Bukit Bintang, Kuala Lumpur'),
 ('KLCC View Hotel','Kuala Lumpur',4,'Jalan Ampang, Kuala Lumpur'),
 ('Georgetown Heritage Inn','Penang',3,'Armenian St, George Town'),
 ('District 1 Saigon Hostel','Ho Chi Minh City',2,'Pham Ngu Lao, District 1'),
 ('Old Quarter Hanoi Hotel','Hanoi',3,'Hang Bac St, Hanoi'),
 ('Deira Budget Hotel','Dubai',2,'Al Rigga Rd, Deira, Dubai'),
 ('Marina Skyline Hotel','Dubai',4,'Dubai Marina'),
 ('Connaught Place Residency','Delhi',3,'Connaught Place, New Delhi'),
 ('Indiranagar Suites','Bangalore',3,'Indiranagar, Bengaluru'),
 ('Koregaon Park Inn','Pune',3,'Koregaon Park, Pune'),
 ('Marine Drive Hotel','Mumbai',4,'Marine Drive, Mumbai'),
 ('T Nagar Comfort','Chennai',3,'T Nagar, Chennai'),
 ('Temple Town Lodge','Bhubaneswar',2,'Old Town, Bhubaneswar')) AS v(hn, city, stars, addr)
JOIN cities ci ON ci.city_name = v.city;

INSERT INTO hotel_pricing (hotel_id, city_id, room_type, price_per_night, currency, is_available)
SELECT h.hotel_id, h.city_id, v.room, v.price, 'INR', v.avail FROM (VALUES
 ('Sukhumvit Budget Inn','Standard Double',1800.00,TRUE),
 ('Sukhumvit Budget Inn','Dorm Bed',650.00,TRUE),
 ('Riverside City Hotel','Deluxe Twin',3200.00,TRUE),
 ('Patong Backpackers Lodge','Fan Room',1300.00,TRUE),
 ('Kata Sea Breeze Resort','Sea View',4200.00,TRUE),
 ('Tonsai Bay Bungalows','Bungalow',1900.00,TRUE),
 ('Bukit Bintang Stay','Standard Double',1700.00,TRUE),
 ('KLCC View Hotel','City View',5200.00,TRUE),
 ('Georgetown Heritage Inn','Heritage Room',2300.00,TRUE),
 ('District 1 Saigon Hostel','Private Room',1100.00,TRUE),
 ('Old Quarter Hanoi Hotel','Deluxe Double',2000.00,TRUE),
 ('Deira Budget Hotel','Standard Double',3100.00,TRUE),
 ('Marina Skyline Hotel','Skyline King',9800.00,TRUE),
 ('Connaught Place Residency','Executive',3500.00,TRUE),
 ('Indiranagar Suites','Studio',3300.00,TRUE),
 ('Koregaon Park Inn','Deluxe',2900.00,TRUE),
 ('Marine Drive Hotel','Sea Facing',6500.00,TRUE),
 ('T Nagar Comfort','Standard',2200.00,TRUE),
 ('Temple Town Lodge','Standard',1400.00,TRUE)) AS v(hn, room, price, avail)
JOIN hotels h ON h.hotel_name = v.hn;

INSERT INTO attractions (city_id, attraction_name, entry_fee_per_person)
SELECT ci.city_id, v.an, v.fee FROM (VALUES
 ('Bangkok','Grand Palace',1250.00), ('Bangkok','Wat Arun',180.00), ('Bangkok','Wat Pho',250.00),
 ('Bangkok','Chatuchak Weekend Market',0.00), ('Bangkok','Jim Thompson House',500.00),
 ('Phuket','Big Buddha',0.00), ('Phuket','Phuket Old Town Walk',0.00),
 ('Phuket','Phang Nga Bay Boat Tour',2600.00), ('Phuket','Wat Chalong',0.00),
 ('Phi Phi Island','Maya Bay Speedboat Tour',3200.00), ('Phi Phi Island','Viewpoint Hike',250.00),
 ('Kuala Lumpur','Petronas Towers Observation Deck',1900.00), ('Kuala Lumpur','Batu Caves',0.00),
 ('Kuala Lumpur','KL Tower',1300.00), ('Kuala Lumpur','Bird Park',950.00),
 ('Penang','Penang Hill Funicular',550.00), ('Penang','Kek Lok Si Temple',0.00),
 ('Ho Chi Minh City','War Remnants Museum',250.00), ('Ho Chi Minh City','Cu Chi Tunnels',1400.00),
 ('Hanoi','Temple of Literature',250.00), ('Hanoi','Hoa Lo Prison Museum',200.00),
 ('Dubai','Burj Khalifa At The Top',4500.00), ('Dubai','Dubai Frame',1300.00),
 ('Dubai','Desert Safari',4200.00), ('Dubai','Dubai Aquarium',3800.00)) AS v(city, an, fee)
JOIN cities ci ON ci.city_name = v.city;
