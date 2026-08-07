# Event Service — Entity Relationship Diagram

**Database:** `event_db` (PostgreSQL)
**Entities:** Event, EventCategory, Section, Seat, SeatLock, ZoneLock

---

## ER Diagram

```mermaid
erDiagram

  EventCategory o|--o{ Event : "categorizes"
  Event ||--o{ Section : "contains"
  Section ||--o{ Seat : "contains"
  Section o|--o{ ZoneLock : "locks a section zone"
  Seat o|--o{ SeatLock : "locks a seat"
  %% ZoneLock is drawn beside Section and SeatLock beside Seat for readability;
  %% real references: ZoneLock.zone_id -> Section.id, SeatLock.seat_id -> Seat.id

  Event {
    UUID id PK
    string title "not null"
    string description "TEXT"
    string location "not null"
    UUID venue_template_id
    UUID event_category_id
    string organization
    UUID created_by_id "nullable"
    string poster_url "not null, TEXT"
    string banner_url "TEXT"
    enum status "PUBLISHED | ACTIVE | CANCELLED | COMPLETED"
    enum bookingModel "ZONE | SEAT | MIXED"
    instant start_date_time "not null"
    instant end_date_time "not null"
    datetime created_at
  }

  EventCategory {
    UUID id PK
    string name "not null, unique"
  }

  Section {
    UUID id PK "auto-generated"
    UUID template_section_id
    UUID event_id FK "not null"
    string name "not null, unique per event"
    int capacity
    int remaining_capacity
    string color
    decimal price
    datetime updated_at
  }

  Seat {
    UUID id PK "auto-generated"
    UUID template_seat_id
    UUID section_id FK "not null"
    string lable "not null"
    enum status "AVAILABLE | BOOKED_ORGANIZER | BOOKED"
    datetime updated_at
  }

  SeatLock {
    UUID id PK
    UUID seat_id "unique, not null"
    string reservation_id "not null"
    datetime expires_at "not null"
    datetime created_at
  }

  ZoneLock {
    UUID id PK
    UUID zone_id "not null"
    string reservation_id "not null"
    int quantity "not null"
    datetime expires_at "not null"
    datetime created_at
  }
```

---

## Entity Definitions

### Event
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, auto-generated | Unique event identifier |
| title | VARCHAR(255) | NOT NULL | Event title |
| description | TEXT | nullable | Event description |
| location | VARCHAR(255) | NOT NULL | Event location |
| venue_template_id | UUID | nullable | Links to venue layout template |
| event_category_id | UUID | FK → EventCategory | Event category |
| organization | VARCHAR(255) | nullable | Organization name (denormalized) |
| created_by_id | UUID | nullable | User who created the event |
| poster_url | TEXT | NOT NULL | Poster image URL |
| banner_url | TEXT | nullable | Banner image URL |
| status | ENUM | NOT NULL | PUBLISHED, ACTIVE, CANCELLED, COMPLETED |
| booking_model | ENUM | NOT NULL | ZONE, SEAT, MIXED |
| start_date_time | TIMESTAMPTZ | NOT NULL | Event start date |
| end_date_time | TIMESTAMPTZ | NOT NULL | Event end date |
| created_at | TIMESTAMP | | Auto-set by Spring Data |

### EventCategory
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, auto-generated | Unique category ID |
| name | VARCHAR(255) | NOT NULL, UNIQUE | Category name |

### Section
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK (auto-generated) | Section ID |
| template_section_id | UUID | nullable | Matches venue template section |
| event_id | UUID | FK → Event, NOT NULL | Parent event |
| name | VARCHAR(255) | NOT NULL, UNIQUE(event_id, name) | Section name |
| capacity | INT | nullable | Total capacity |
| remaining_capacity | INT | nullable | Available seats |
| color | VARCHAR(255) | nullable | Display color |
| price | DECIMAL | nullable | Base price |
| updated_at | TIMESTAMP | | Auto-set by Spring Data |

### Seat
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK (auto-generated) | Seat ID |
| template_seat_id | UUID | nullable | Matches venue template seat |
| section_id | UUID | FK → Section, NOT NULL | Parent section |
| lable | VARCHAR(255) | NOT NULL | Seat label |
| status | ENUM | NOT NULL | AVAILABLE, BOOKED_ORGANIZER, BOOKED |
| updated_at | TIMESTAMP | | Auto-set by Spring Data |

### SeatLock
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, auto-generated | Lock record ID |
| seat_id | UUID | NOT NULL, UNIQUE | Locked seat (no FK constraint) |
| reservation_id | VARCHAR(255) | NOT NULL, indexed | Saga reservation identifier |
| expires_at | TIMESTAMP | NOT NULL, indexed | Lock expiration |
| created_at | TIMESTAMP | | Auto-set by Spring Data |

### ZoneLock
| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, auto-generated | Lock record ID |
| zone_id | UUID | NOT NULL, indexed | Locked section ID (zone) |
| reservation_id | VARCHAR(255) | NOT NULL, indexed | Saga reservation identifier |
| quantity | INT | NOT NULL | Number of tickets locked |
| expires_at | TIMESTAMP | NOT NULL, indexed | Lock expiration |
| created_at | TIMESTAMP | | Auto-set by Spring Data |

---

## Relationship Summary

| Parent | Child | Type | FK Column | Source File |
|--------|-------|------|-----------|-------------|
| Event | Section | OneToMany | event_id | `Event.java:73` |
| Section | Seat | OneToMany | section_id | `Section.java:63` |
| Event | EventCategory | ManyToOne | event_category_id | `Event.java:43` |
| Section | Event | ManyToOne | event_id | `Section.java:44-45` |
| Seat | Section | ManyToOne | section_id | `Seat.java:31-32` |
| Seat (ref) | SeatLock | Reference (UUID) | seat_id | `SeatLock.java:24` |
| Section (ref) | ZoneLock | Reference (UUID) | zone_id | `ZoneLock.java:25` |
