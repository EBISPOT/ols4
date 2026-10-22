import { marked } from "marked";
import DOMPurify from "dompurify";

export default function ReleaseNotes({ notes }: { notes: string }) {
  const html = DOMPurify.sanitize(marked.parse(notes, { async: false }), {
    ALLOWED_TAGS: ["h1", "h2", "h3", "h4", "h5", "h6", "p", "ul", "ol", "li", "a", "strong", "em", "del", "code", "pre", "blockquote", "hr", "br", "table", "thead", "tbody", "tr", "th", "td"],
    ALLOWED_ATTR: ["href", "title"],
  });
  return <div className="release-notes break-words mb-4" dangerouslySetInnerHTML={{ __html: html }} />;
}
