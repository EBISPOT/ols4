import { Checkbox, FormControlLabel } from "@mui/material";
import { Fragment, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { get, getPaginated } from "../app/api";
import { randomString } from "../app/util";
import Entity from "../model/Entity";
import { thingFromProperties } from "../model/fromProperties";
import Ontology from "../model/Ontology";
import { Suggest } from "../model/Suggest";
import Thing from "../model/Thing";

let curSearchToken:any = null;

export default function SearchBox({
	initialQuery,
	placeholder,
	ontologyId}:{
		initialQuery?:string,
		placeholder?:string,
		ontologyId?:string
	}) {

  const [open, setOpen] = useState<boolean>(false);
  let [autocomplete, setAutocomplete] = useState<Suggest|null>(null)
  let [jumpTo, setJumpTo] = useState<Thing[]>([])
  let [loading, setLoading] = useState<boolean>(true);
  let [query, setQuery] = useState<string>(initialQuery || "");
  //const homeSearch = document.getElementById("home-search") as HTMLInputElement;

  let [exact, setExact] = useState<boolean>(false);
  let [obsolete, setObsolete] = useState<boolean>(false);

  const searchForOntologies = ontologyId === undefined
  const showSuggestions = ontologyId === undefined

  const navigate = useNavigate();

  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  });

  useEffect(() => {

	async function loadSuggestions() {
		setLoading(true)


		let searchToken = randomString();
		curSearchToken = searchToken;

		let [entities, ontologies, autocomplete] = await Promise.all([
			getPaginated<any>(
				`api/v2/entities?${new URLSearchParams({
					search: query,
					size: "5",
					lang: 'en',
					exactMatch: exact.toString(),
					includeObsoleteEntities: obsolete.toString(),
					...(ontologyId ? {ontologyId} : {})
				})}`
			),
			searchForOntologies ? getPaginated<any>(
				`api/v2/ontologies?${new URLSearchParams({
					search: query,
					size: "5",
					lang: 'en',
					exactMatch: exact.toString(),
					includeObsoleteEntities: obsolete.toString(),
				})}`
			) : null,
			showSuggestions ? get<Suggest>(
				`api/suggest?${new URLSearchParams({
					q: query,
					exactMatch: exact.toString(),
					includeObsoleteEntities: obsolete.toString(),
				})}`
			) : null,
		]);

		if(searchToken === curSearchToken) {
			setJumpTo([
				...entities.elements.map((obj) => thingFromProperties(obj)),
				...(ontologies?.elements.map((obj) => new Ontology(obj)) || [])
			])
			setAutocomplete(autocomplete)
			setLoading(false)
		}
	}

	loadSuggestions()

  }, [ query, exact, obsolete ])

  let show = open && !!query

	return <Fragment>
		<div className="relative w-full self-center">
			<div className="flex space-x-4">
				<div className="grow">
			<input
			id="home-search"
			type="text"
			autoComplete="off"
			placeholder={placeholder||"Search OLS..."}
			className="input-default text-lg focus:rounded-b-sm focus-visible:rounded-b-sm pl-3"
			onFocus={() => {
				setOpen(true);
			}}
			onBlur={() => {
				setTimeout(function () {
					if (mounted.current) setOpen(false);
				}, 500);
			}}
			value={query}
			onChange={(e) => {
				setQuery(e.target.value);
			}}
		/>
			<div
				className={
					loading
						? "spinner-default w-7 h-7 absolute right-3 top-2.5 z-10"
						: "hidden"
				}
			/>
			<ul
				className={
					show
						? "list-none bg-white text-neutral-dark border-2 border-neutral-dark shadow-input rounded-b-md w-full absolute left-0 top-12 z-10"
						: "hidden"
				}
			>

				{autocomplete?.response.docs &&
					autocomplete.response.docs.slice(0, 5).map(autocomplete => {
						return <li
							key={randomString()}
							className="py-0 px-3 leading-7 hover:bg-link-light hover:rounded-sm hover:cursor-pointer"
						>{autocomplete.autosuggest}</li>
					})
				}

				{jumpTo && <li className="py-2 px-3 leading-7 hover:bg-link-light hover:rounded-sm hover:cursor-pointer"
				><b>Jump to</b></li>}

				{jumpTo && jumpTo.map((jumpToEntry: Thing) => {
					const termUrl = encodeURIComponent(
						encodeURIComponent(jumpToEntry.getIri())
					);
					return <Fragment>
						{jumpToEntry instanceof Entity && (
							// TODO which names to show? (multilang = lots of names)
							jumpToEntry.getNames().splice(0, 1).map(name => (
								<li
									key={randomString()}
									className="py-2 px-3 leading-7 hover:bg-link-light hover:rounded-sm hover:cursor-pointer"
								>
									<Link
										onClick={() => {
											setOpen(false);
										}}
										to={`/ontologies/${jumpToEntry.getOntologyId()}/${jumpToEntry.getTypePlural()}/${termUrl}`}
									>
										<div className="flex justify-between">
											<div
												className="truncate flex-auto"
												title={name}
											>
												{name}
											</div>
											<div className="truncate flex-initial ml-2 text-right">
												<span
													className="mr-2 bg-link-default px-3 py-1 rounded-lg text-sm text-white uppercase"
													title={jumpToEntry.getOntologyId()}
												>
													{jumpToEntry.getOntologyId()}
												</span>
												<span
													className="bg-orange-default px-3 py-1 rounded-lg text-sm text-white uppercase"
													title={jumpToEntry.getShortForm()}
												>
													{jumpToEntry.getShortForm()}
												</span>
											</div>
										</div>
									</Link>
								</li>
							)))}
					</Fragment>
				})}

				{jumpTo && jumpTo.map((jumpToEntry: Thing) => {
					const termUrl = encodeURIComponent(
						encodeURIComponent(jumpToEntry.getIri())
					);
					return <Fragment>
						{jumpToEntry instanceof Ontology && (
							jumpToEntry.getNames().map(name => (
								<li
									key={randomString()}
									className="py-0 px-3 leading-7 hover:bg-link-light hover:rounded-sm hover:cursor-pointer"
								>
									<Link
										onClick={() => {
											setOpen(false);
										}}
										to={"/ontologies/" + jumpToEntry.getOntologyId()}
									>
										<div className="flex">
											<span
												className="truncate text-link-dark font-bold"
												title={
													jumpToEntry.getName() || jumpToEntry.getOntologyId()
												}
											>
												{jumpToEntry.getName() || jumpToEntry.getOntologyId()}
											</span>
										</div>
									</Link>
								</li>
							)))}
					</Fragment>
				}
				)}

				<hr />
				{ query &&
				<li
					key={randomString()}
					className="py-1 px-3 leading-7 hover:bg-link-light hover:rounded-sm hover:cursor-pointer"
				>Search OLS for <b>{query}</b></li> }
			</ul>
		</div>
		<div>
		<button
			className="button-primary text-lg font-bold self-center"
			onClick={() => {
				if (query) {
					navigate("/search/" + query)
				}
			}}
		>
			Search
		</button>
		</div>
		</div>
		<div className="col-span-2">
		 <FormControlLabel control={<Checkbox value={exact} onChange={(ev) => setExact(!!ev.target.checked)} />} label="Exact match" />
		 <FormControlLabel control={<Checkbox value={obsolete} onChange={(ev) => setObsolete(!!ev.target.checked)}  />} label="Include obsolete terms" />
		 </div>
		 </div>
</Fragment >


}
