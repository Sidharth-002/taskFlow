import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listTickets } from "../api/tickets";
import { extractErrorMessage } from "../api/client";
import type { Page, TicketListItem, TicketStatus } from "../types";

const STATUSES: TicketStatus[] = ["OPEN", "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED"];

export function TicketsPage() {
  const [page, setPage] = useState<Page<TicketListItem> | null>(null);
  const [pageNumber, setPageNumber] = useState(0);
  const [status, setStatus] = useState<TicketStatus | "">("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listTickets({ status: status || undefined, page: pageNumber, size: 20 })
      .then(setPage)
      .catch((err) => setError(extractErrorMessage(err)))
      .finally(() => setLoading(false));
  }, [status, pageNumber]);

  return (
    <div className="page">
      <div className="page-header">
        <h1>Tickets</h1>
        <Link to="/tickets/new" className="button">
          New ticket
        </Link>
      </div>

      <label className="filter">
        Status
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as TicketStatus | "");
            setPageNumber(0);
          }}
        >
          <option value="">All</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </label>

      {error && <p className="error">{error}</p>}
      {loading && <p className="loading">Loading…</p>}

      {!loading && page && (
        <>
          <table className="ticket-table">
            <thead>
              <tr>
                <th>Title</th>
                <th>Status</th>
                <th>Priority</th>
                <th>Project</th>
                <th>Assignee</th>
                <th>Due</th>
              </tr>
            </thead>
            <tbody>
              {page.content.map((ticket) => (
                <tr key={ticket.id}>
                  <td>
                    <Link to={`/tickets/${ticket.id}`}>{ticket.title}</Link>
                  </td>
                  <td>
                    <span className={`badge status-${ticket.status.toLowerCase()}`}>{ticket.status}</span>
                  </td>
                  <td>{ticket.priority}</td>
                  <td>{ticket.projectName}</td>
                  <td>{ticket.assignedToName ?? "Unassigned"}</td>
                  <td>{ticket.dueDate ? new Date(ticket.dueDate).toLocaleDateString() : "—"}</td>
                </tr>
              ))}
              {page.content.length === 0 && (
                <tr>
                  <td colSpan={6}>No tickets found.</td>
                </tr>
              )}
            </tbody>
          </table>

          <div className="pagination">
            <button disabled={page.number === 0} onClick={() => setPageNumber((n) => n - 1)}>
              Previous
            </button>
            <span>
              Page {page.number + 1} of {Math.max(page.totalPages, 1)} ({page.totalElements} total)
            </span>
            <button disabled={page.number + 1 >= page.totalPages} onClick={() => setPageNumber((n) => n + 1)}>
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
}
