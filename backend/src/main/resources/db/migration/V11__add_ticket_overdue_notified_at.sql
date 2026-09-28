-- Tracks whether OverdueTicketCheckJob has already published a
-- TicketOverdueEvent for this ticket, so the job doesn't re-notify on
-- every run for a ticket that's still overdue from a previous check.
ALTER TABLE tickets
    ADD COLUMN overdue_notified_at TIMESTAMPTZ;
