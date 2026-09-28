import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { extractErrorMessage } from "../api/client";

/**
 * Provisions a brand-new organization with the submitter as its first
 * user (ORG_ADMIN) - matching the backend's `RegisterRequest`, there is no
 * "join an existing organization" flow. Adding further users (any other
 * role) is an ORG_ADMIN-only API action with no UI here - see the
 * frontend's README section for why.
 */
export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [organizationName, setOrganizationName] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register({ organizationName, firstName, lastName, email, password });
      navigate("/tickets");
    } catch (err) {
      setError(extractErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-page">
      <form className="auth-form" onSubmit={handleSubmit}>
        <h1>Register your organization</h1>
        {error && <p className="error">{error}</p>}
        <label>
          Organization name
          <input value={organizationName} onChange={(e) => setOrganizationName(e.target.value)} required autoFocus />
        </label>
        <label>
          First name
          <input value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
        </label>
        <label>
          Last name
          <input value={lastName} onChange={(e) => setLastName(e.target.value)} required />
        </label>
        <label>
          Email
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} />
          <small>At least 8 characters, with both letters and numbers.</small>
        </label>
        <button type="submit" disabled={submitting}>
          {submitting ? "Creating…" : "Create organization"}
        </button>
        <p>
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </div>
  );
}
