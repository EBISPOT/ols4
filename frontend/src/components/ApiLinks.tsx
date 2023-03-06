import { Link } from "react-router-dom";

export default function ApiLinks({apiUrl}:{apiUrl:string}) {

	return <Link to={apiUrl} target="_blank">
		<img src={process.env.PUBLIC_URL + '/json.svg'} width={80} />
		</Link>
}
