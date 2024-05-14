export function Banner({
  type,
  children,
}: {
  type: "code" | "info" | "warning" | "error";
  children: any;
}) {
  const bgColor = {
    code: "bg-neutral-light",
    info: "bg-blue-50",
    warning: "bg-yellow-200",
    error: "bg-red-300",
  }[type];

  return (
    <div
      className={`${bgColor} px-6 pt-3 pb-4 rounded-md mb-4 text-justify overflow-x-auto ${
        type === "code"
          ? "font-mono text-sm whitespace-nowrap"
          : ""
      }`}
    >
      <div style={{display: 'flex', alignItems: 'center'}}>
        {type === "info" && (
            <i className="icon icon-common icon-info text-2xl text-blue-500 mr-2"/>
        )}
        {type === "warning" && (
            <i className="icon icon-common icon-exclamation-triangle text-2xl text-yellow-800 mr-2"/>
        )}
        {type === "error" && (
            <i className="icon icon-common icon-exclamation-circle text-2xl text-red-500 mr-2"/>
        )}
        <div dangerouslySetInnerHTML={{__html: children}}/>
      </div>
    </div>
  );
}
