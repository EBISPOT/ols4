import { Fragment, useEffect, useState } from "react";
import Header from "../components/Header";

interface DefinedField {
    ols4FieldName: string;
    ols3FieldName: string;
    description: string;
    dataType: string;
}

export default function StaticDocs() {
    document.title = "OLS Documentation";

    const [fields, setFields] = useState<DefinedField[]>([]);

    useEffect(() => {
        fetch(process.env.REACT_APP_APIURL+'api/v2/defined-fields')
            .then((response) => response.json())
            .then((data) => setFields(data))
            .catch((error) => console.error('Error fetching defined fields:', error));
    }, []);

    return (
        <Fragment>
            <Header section="static-docs" />
            <main className="container mx-auto px-4 my-8">
                <div className="text-2xl font-bold my-6">Defined Response Fields in OLS</div>
                <table className="table-auto w-full border-collapse border border-gray-300">
                    <thead>
                    <tr className="bg-gray-100">
                        <th className="border border-gray-300 px-4 py-2">OLS4 Field Name</th>
                        <th className="border border-gray-300 px-4 py-2">OLS3 Field Name</th>
                        <th className="border border-gray-300 px-4 py-2">Description</th>
                        <th className="border border-gray-300 px-4 py-2">Data Type</th>
                    </tr>
                    </thead>
                    <tbody>
                    {fields.map((field, index) => (
                        <tr key={index}>
                            <td className="border border-gray-300 px-4 py-2">{field.ols4FieldName}</td>
                            <td className="border border-gray-300 px-4 py-2">{field.ols3FieldName}</td>
                            <td className="border border-gray-300 px-4 py-2">{field.description}</td>
                            <td className="border border-gray-300 px-4 py-2">{field.dataType}</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </main>
        </Fragment>
    );
}
