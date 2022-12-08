import Header from "../../components/Header";

export default function SearchResult({ search }: { search: string }) {
  return (
    <div>
      <Header section="home" />
      <main className="container mx-auto">
        <div className="my-8 mx-2">Searching for: {search}</div>
      </main>
    </div>
  );
}
