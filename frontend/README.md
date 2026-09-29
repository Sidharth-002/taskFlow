# FlowDesk frontend

A React + TypeScript single-page app for the FlowDesk API: a Jira-style
workspace with a Kanban board, issue tracking, team and people management,
a reporting dashboard and a notification inbox.

## Stack

- **React 18 + TypeScript**, scaffolded with Vite (react-ts template)
- **React Router v6** for client-side routing
- **Axios** for HTTP, with a single interceptor-equipped client
  (`src/shared/api/client.ts`) handling JWT attachment and refresh-token rotation
- Plain CSS (`src/shared/ui/theme.css`) with design tokens, dark by default
  with a light theme - no component library, icon package, chart library or
  drag-and-drop library. Icons are inline SVG, charts are hand-drawn SVG, and
  the board uses native HTML5 drag and drop.

No test framework or state-management library is set up - see the root
README for why.

## What's implemented

| Page | What it does |
|---|---|
| **Dashboard** | Animated stat cards, status donut, priority breakdown, created-vs-closed this week, workload per person and per team, "My work" and "On fire" lists. `ORG_ADMIN`/`TEAM_LEAD` get `/api/dashboard/summary`; other roles see the same view computed from the tickets they can see. |
| **Board** | Kanban across the five statuses. Drag a card and only the columns the workflow allows light up (`OPEN → IN_PROGRESS`, never `OPEN → CLOSED`); moves are optimistic and roll back if the server rejects them. Filters (text, mine, priority, project, team, assignee) live in the URL so a filtered board can be shared. Closing or resolving fires confetti. |
| **Issues** | Server-side filtered, sorted, paginated table. |
| **Issue detail** | Inline title/description editing, workflow stepper with one-click transitions, assignee/team/priority/due-date editing, comments (edit/delete your own), and an activity timeline from the audit log. |
| **Projects** | Cards with per-status progress, open/overdue counts and contributors; admins create, edit and archive. |
| **Teams** | Team list with open-issue load, lead assignment, add/remove members, create/edit/deactivate. |
| **People** | Everyone in the org with role counts and workload; admins add people, change roles and (de)activate. |
| **Inbox** | Notifications grouped by day, unread filter, mark all read; unread count badge polls every 30s. |

Plus: a **command palette** (`Ctrl/⌘ K` or `/`) that searches issues, jumps to
`FD-42` directly, and navigates anywhere; **keyboard shortcuts** (`C` to create
an issue, `g` then `d`/`b`/`i`/`p`/`t`/`u`/`n` to switch pages); a collapsible
sidebar; and a dark/light toggle.

Every action is shown only to roles the backend allows to take it
(`shared/lib/permissions.ts` mirrors the `@PreAuthorize` rules). The server
remains the source of truth.

**Not possible through the API:** clearing a ticket's assignee, team or due
date once set - `PATCH /api/tickets/{id}` can't distinguish "leave unchanged"
from "clear" (see `UpdateTicketRequest`'s Javadoc on the backend).

## Running locally

```bash
cp .env.example .env   # only needed if your backend isn't on localhost:8080
npm install
npm run dev
```

Requires the backend running (see the root README) - by default at
`http://localhost:8080`, already permitted by its CORS configuration for
`http://localhost:5173` (Vite's default dev port).

```bash
npm run build   # type-checks (tsc -b) then produces dist/
npm run lint
```

## Structure

Organized by feature, mirroring the backend's packages:

```
src/
├── app/                 App shell and cross-feature wiring
│   ├── App.tsx          Routes
│   ├── AppShell.tsx     Sidebar, top bar, global shortcuts, unread polling
│   ├── DirectoryContext Users/teams/projects, loaded once, shared by every picker
│   ├── ShellContext     openCreateTicket / openCommandPalette for any page
│   ├── navigation.ts    Nav items and their "g" shortcut keys
│   └── theme.ts         Dark/light preference
├── features/
│   ├── auth/            Login, register, AuthContext, ProtectedRoute
│   ├── tickets/         Board, issue list, issue detail, create modal,
│   │                    workflow.ts (statuses, transitions, colours)
│   ├── dashboard/       Dashboard page and its SVG charts
│   ├── projects/  teams/  people/  notifications/
│   └── command/         Command palette
└── shared/
    ├── api/             Axios client (JWT + refresh rotation), token storage
    ├── lib/             Permissions, formatting, hooks (useAsync, useCountUp…)
    ├── ui/              Icons, avatars, modal, toasts, confetti, theme.css
    └── types.ts         Cross-feature DTO shapes
```

Each feature owns its `api.ts` (typed calls for that backend resource) and,
where needed, its `types.ts`. `shared/` never imports from `features/`.

## Notable decisions

**Tokens in `localStorage`, not an httpOnly cookie.** A successful XSS
against this app could read them, which a cookie the client-side JS never
sees would prevent. Accepted here because it keeps the client simple (no
CSRF token dance, no cookie-domain configuration) - a production frontend
would very likely make the opposite trade-off.

**Refresh-token rotation, deduplicated.** The backend's refresh tokens are
one-time-use. If two requests both hit a 401 for an expired access token at
the same moment, `client.ts` makes the second wait on the same in-flight
refresh call rather than each calling `/api/auth/refresh` - the loser of that
race would present an already-rotated-away token and fail.

**The ticket workflow is mirrored client-side** (`features/tickets/workflow.ts`).
An earlier version deliberately didn't do this, to avoid a second copy of
`TicketWorkflow` that could drift. The board changed the trade-off: showing
which columns accept a dragged card, and offering only legal transitions on
the detail page, needs the rules up front. The server still validates every
move, so drift would show up as a rejected move and an error toast - not as
an illegal transition getting through. Keep the two in sync when the
workflow changes.

**Board and dashboard load every visible ticket in one request**
(`size=500`) and filter client-side, so filters are instant. That's fine at
this app's scale; a large organization would need server-side board
queries.
