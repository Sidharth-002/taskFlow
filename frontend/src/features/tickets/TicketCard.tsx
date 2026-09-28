import { useState, type DragEvent } from "react";
import { Link } from "react-router-dom";
import type { TicketListItem, TicketStatus } from "./types";
import { STATUS_META, TRANSITIONS, isOverdue } from "./workflow";
import { DueBadge, PriorityIcon } from "./TicketBadges";
import { Avatar } from "../../shared/ui/Avatar";
import { Icon } from "../../shared/ui/Icon";
import { ticketKey } from "../../shared/lib/format";

export function TicketCard({
  ticket,
  draggable,
  dragging,
  onDragStart,
  onDragEnd,
  onMove,
}: {
  ticket: TicketListItem;
  draggable: boolean;
  dragging: boolean;
  onDragStart: (e: DragEvent, ticket: TicketListItem) => void;
  onDragEnd: () => void;
  onMove: (ticket: TicketListItem, to: TicketStatus, origin: { x: number; y: number }) => void;
}) {
  const [menuOpen, setMenuOpen] = useState(false);
  const next = TRANSITIONS[ticket.status];

  return (
    <article
      className={`ticket-card prio-${ticket.priority.toLowerCase()} ${dragging ? "dragging" : ""} ${
        isOverdue(ticket) ? "is-overdue" : ""
      }`}
      draggable={draggable}
      onDragStart={(e) => onDragStart(e, ticket)}
      onDragEnd={onDragEnd}
    >
      <Link to={`/tickets/${ticket.id}`} className="ticket-card-title" draggable={false}>
        {ticket.title}
      </Link>
      <div className="ticket-card-tags">
        <span className="tag">{ticket.projectName}</span>
        {ticket.teamName && <span className="tag tag-team">{ticket.teamName}</span>}
        <DueBadge dueDate={ticket.dueDate} status={ticket.status} />
      </div>
      <footer className="ticket-card-foot">
        <PriorityIcon priority={ticket.priority} />
        <span className="ticket-key">{ticketKey(ticket.id)}</span>
        <span className="spacer" />
        {draggable && next.length > 0 && (
          <span className="quick-move">
            <button
              className="icon-btn icon-btn-sm"
              aria-label={`Move ${ticketKey(ticket.id)}`}
              aria-expanded={menuOpen}
              onClick={() => setMenuOpen((o) => !o)}
            >
              <Icon name="arrowRight" size={14} />
            </button>
            {menuOpen && (
              <>
                <span className="menu-backdrop" onClick={() => setMenuOpen(false)} />
                <span className="menu menu-sm" role="menu">
                  {next.map((to) => (
                    <button
                      key={to}
                      role="menuitem"
                      className="menu-item"
                      onClick={(e) => {
                        setMenuOpen(false);
                        onMove(ticket, to, { x: e.clientX, y: e.clientY });
                      }}
                    >
                      <span className="dot" style={{ background: STATUS_META[to].color }} />
                      {STATUS_META[to].label}
                    </button>
                  ))}
                </span>
              </>
            )}
          </span>
        )}
        <Avatar name={ticket.assignedToName} size={24} />
      </footer>
    </article>
  );
}
