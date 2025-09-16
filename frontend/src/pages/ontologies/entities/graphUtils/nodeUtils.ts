export const getShortFormFromIri = (iri: string): string => {
    return iri.split('/').pop() || iri.split('#').pop() || iri;
};