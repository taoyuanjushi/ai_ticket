import { Link } from "react-router-dom";
import { Button } from "../components/Button";
import { PageHeader } from "../components/PageHeader";

export function ForbiddenPage() {
  return (
    <div>
      <PageHeader eyebrow="403" title="Forbidden" description="Your current role cannot access this page." />
      <div className="px-5 py-5">
        <Link to="/">
          <Button variant="primary">Back to Ticket Desk</Button>
        </Link>
      </div>
    </div>
  );
}
