package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.entity.TicketPriority;
import com.flowdesk.ticket.entity.TicketStatus;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Shared by both {@code PUT} and {@code PATCH /api/tickets/{id}} - every
 * field is optional, and only the ones present in the request are applied
 * (see {@code TicketService.update}). This is a deliberate simplification
 * over strict REST semantics (where {@code PUT} would mean "replace the
 * full resource"): the ticket workflow's real constraint is which
 * <em>status transitions</em> are legal, not whether every field was
 * resupplied on every call, so both verbs share one partial-update
 * implementation rather than duplicating it.
 *
 * <p>Known limitation: because a missing field and an explicit
 * {@code null} are indistinguishable here, {@code teamId}/{@code assignedToId}
 * can be changed to another value but not explicitly cleared back to
 * {@code null} (unassigned) through this endpoint. A real JSON Merge
 * Patch implementation would use a wrapper type to distinguish "absent"
 * from "explicitly null"; adding that is deferred until a real need for
 * "unassign" comes up.
 */
public record UpdateTicketRequest(

        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 10_000, message = "Description must be at most 10,000 characters")
        String description,

        TicketStatus status,

        TicketPriority priority,

        Long teamId,

        Long assignedToId,

        Instant dueDate) {
}
