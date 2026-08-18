# Event Cancellation Flow

## 1. Admin / Head Cancels an Event

* An **Admin** or **Head** initiates an event cancellation request.

---

## 2. Validate Cancellation Window

The **Event Service** verifies whether the event is eligible for cancellation.

**Rule:**

* The event **must start more than 2 days from now**.
* If the event starts in **2 days or less**, the cancellation request is rejected.

---

## 3. Mark Event as Cancelled

If validation succeeds, the **Event Service**:

* Marks the event as **Cancelled**.
* Prevents any new reservations or bookings for the event.

---

## 4. Notify Reservation Service

The **Event Service** publishes a cancellation request to the **Reservation Service**, including the `eventId`.

The Reservation Service must locate all reservations associated with the cancelled event.

---

## 5. Create Cancellation Processing Records

For every reservation related to the event, insert a record into a cancellation processing table.

| Column          | Description                                        |
| --------------- | -------------------------------------------------- |
| `id`            | Auto-increment integer (used as processing cursor) |
| `reservationId` | Reservation identifier                             |
| `paymentStatus` | Payment cancellation status                        |
| `ticketStatus`  | Ticket cancellation status                         |
| `retryCount`    | Number of retry attempts                           |

---

## 6. Initial Status

Every inserted record starts with:

| Field           | Value     |
| --------------- | --------- |
| `paymentStatus` | `PENDING` |
| `ticketStatus`  | `PENDING` |
| `retryCount`    | `0`       |

---

## 7. Background Batch Job

A scheduled job periodically processes cancellation records in batches.

Example:

```text
Batch Size = 10
```

Each batch performs the required payment and ticket cancellation operations.

---

## 8. Successful Processing

If processing succeeds:

* Update the corresponding status from `PENDING` → `DONE`.
* Publish events for:

  * Audit Service
  * Notification Service
  * Analytics Service

---

## 9. Failed Processing

If processing fails:

* Update the corresponding status from `PENDING` → `FAILED`.
* Increment the retry counter.

### 9.1 Exponential Backoff

The scheduler increases the delay between executions when failures occur.

Example progression:

```text
10s
20s
40s
80s
160s
...
Maximum = 15 minutes
```

This reduces unnecessary retries while downstream services are unavailable.

### 9.2 Reset Delay on Success

Once a batch is processed successfully, the scheduler resets to its normal execution interval.

---

## 10. Cursor-Based Batch Processing

To avoid scanning previously processed failed records on every execution, use the auto-increment `id` as a processing cursor.

Example:

```sql
-- First batch
SELECT *
FROM cancellation_table
WHERE id > 10
LIMIT 10;

-- Second batch
SELECT *
FROM cancellation_table
WHERE id > 20
LIMIT 10;
```

The cursor advances after every processed batch, ensuring the job only scans new records instead of repeatedly reading the same failed rows.

---

## 11. Continue Until Completion

The scheduled job continues processing batches until every reservation associated with the cancelled event has been processed successfully.

---

## 12. Cleanup

After each processed batch, execute a cleanup query:

```sql
DELETE
FROM cancellation_table
WHERE payment_status = 'DONE'
  AND ticket_status = 'DONE';
```

Because the deletion occurs immediately after processing a single batch, at most one batch of records is removed each execution, preventing unnecessary database load.
