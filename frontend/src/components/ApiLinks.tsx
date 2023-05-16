import { Link } from "react-router-dom";

export default function ApiLinks({
  apiUrl,
  betaApiUrl,
}: {
  apiUrl: string;
  betaApiUrl: string;
}) {
  return (
    <div className="flex gap-1" style={{ padding: 0 }}>
      <div>
        <Link to={apiUrl} target="_blank" rel="noopener noreferrer">
          <img
            src={process.env.PUBLIC_URL + "/json.svg"}
            width={45}
            alt="JSON document"
          />
        </Link>
      </div>
      <div>
        <Link to={betaApiUrl} target="_blank" rel="noopener noreferrer">
          <img
            src={process.env.PUBLIC_URL + "/jsonbeta.svg"}
            width={45}
            alt="JSON beta document"
          />
        </Link>
      </div>
    </div>
  );
}
