import { useCallback, useState } from "react";

export function Tabs({
  value,
  children,
  onChange,
}: {
  value: string;
  children: JSX.Element[];
  onChange?: (value: string) => void;
}) {
  const firstTab = children ? children[0].props.value : "";
  const initialTab = value ? value : firstTab;
  const [activeTab, setActiveTab] = useState(initialTab);
  const handleActiveTab = useCallback((val) => {
    setActiveTab(val);
    if (onChange) onChange(val);
  }, []);

  return (
    <div className="">
      <div className="w-fit">
        {children && Array.isArray(children)
          ? children.map((child: JSX.Element) => (
              <button
                onClick={(e) => {
                  e.preventDefault();
                  handleActiveTab(child.props.value);
                }}
                className={`border-b-2 border-b-black py-2 px-4 text-lg font-semibold ${
                  child.props.value === activeTab
                    ? "text-link-default border-b-4 border-b-link-default"
                    : "text-link-tab pb-2.5 hover:pb-2 hover:text-black hover:border-b-4 hover:border-b-link-tab"
                }`}
                key={child.props.value}
              >
                {child.props.label}
              </button>
            ))
          : null}
      </div>
      <div className="">
        {children && Array.isArray(children)
          ? children.filter(
              (child: JSX.Element) => child.props.value === activeTab
            )
          : null}
      </div>
    </div>
  );
}

export function Tab({
  children,
}: {
  label: string;
  value: string;
  children?: React.ReactNode;
}) {
  return <div className="">{children}</div>;
}
