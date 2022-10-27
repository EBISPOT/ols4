import { useCallback, useState } from "react";

export function Tabs({
  value,
  children,
  onChange,
}: {
  value: any;
  children: JSX.Element[];
  onChange?: (value: any) => void;
}) {
  const firstTab = children ? children[0].props.value : "";
  const initialTab = value ? value : firstTab;
  const [activeTab, setActiveTab] = useState(initialTab);
  const handleActiveTab = useCallback((val) => {
    setActiveTab(val);
    if (onChange) onChange(val);
  }, []);

  return (
    <div>
      <div className="w-fit">
        {children && Array.isArray(children)
          ? children.map((child: JSX.Element) => (
              <button
                onClick={(e) => {
                  e.preventDefault();
                  handleActiveTab(child.props.value);
                }}
                className={`text-link-tab border-b-2 border-b-black py-2 px-4 text-lg font-bold disabled:pb-2.5 disabled:text-link-tab disabled:border-b-2 disabled:cursor-not-allowed ${
                  child.props.value === activeTab
                    ? "text-link-default border-b-4 border-b-link-default"
                    : "pb-2.5 hover:pb-2 hover:text-black hover:border-b-4 hover:border-b-link-tab"
                }`}
                key={child.props.value}
                disabled={child.props.disabled}
              >
                {child.props.label}
              </button>
            ))
          : null}
      </div>
      <div>
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
  disabled?: boolean;
  children?: React.ReactNode;
}) {
  return <div>{children}</div>;
}
