# FlowDesk frontend

A React + TypeScript single-page app for the FlowDesk API - intentionally
simple (see the root [README](../README.md#frontend)), not a full
feature-parity client for every backend capability.

## Stack

- **React 18 + TypeScript**, scaffolded with Vite (react-ts template)
- **React Router v6** for client-side routing
- **Axios** for HTTP, with a single interceptor-equipped client
  (`src/api/client.ts`) handling JWT attachment and refresh-token rotation
- Plain CSS (`src/index.css`) - no component library or utility framework

No test framework or state-management library is set up - see the root
README for why that's a deliberate scope decision here, not an oversight.

## What's implemented

- Register (creates a new organization + its first `ORG_ADMIN`) and login
- Ticket list, with a status filter and pagination
- Create a ticket (with an inline "create a project first" flow if the
  organization has none yet - every ticket needs one)
- Ticket detail: view fields, change status, add/view comments
- Notifications: list and mark as read

**Not implemented** (all admin-only actions in the backend, with no
self-service equivalent a plain user could reach): team management, user
management, the reporting dashboard, and audit log viewing. Also not
implemented: reassigning a ticket's team/assignee, and clearing a ticket's
due date once set (the backend's `PATCH` endpoint can't distinguish "leave
unchanged" from "clear" either - see `UpdateTicketRequest`'s Javadoc on
the backend).

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

```
src/
├── api/          Typed Axios calls, one module per backend resource,
│                 plus client.ts (the interceptor-equipped instance) and
│                 tokenStorage.ts (localStorage access, centralized)
├── context/      AuthContext - current user, login/register/logout
├── components/   NavBar, ProtectedRoute
├── pages/        One component per route
└── types.ts      TypeScript shapes mirroring the backend's DTOs
```

## Notable decisions

**Tokens in `localStorage`, not an httpOnly cookie.** A successful XSS
against this app could read them, which a cookie the client-side JS never
sees would prevent. Accepted here because it keeps the client simple (no
CSRF token dance, no cookie-domain configuration) and matches this
frontend's "intentionally simple" scope - a real production frontend
would very likely make the opposite trade-off.

**Refresh-token rotation, deduplicated.** The backend's refresh tokens are
one-time-use (rotation - see the backend README's authentication flow
section). If two requests both hit a 401 for an expired access token at
the same moment, `client.ts` makes the second one wait on the same
in-flight refresh call rather than each independently calling
`/api/auth/refresh` - the loser of that race would present an
already-rotated-away token and fail.

**No client-side replica of the ticket workflow's legal-transition
rules.** The status dropdown on a ticket offers every status; an illegal
transition is rejected by the backend (`409 INVALID_TICKET_TRANSITION`)
and the returned message is just displayed. Duplicating
`TicketWorkflow`'s state machine in TypeScript would be one more place for
the two to drift out of sync, for a modest UX improvement (a slightly
smaller dropdown) that wasn't judged worth it here.
