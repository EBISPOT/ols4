export default function Node({
  expandable,
  expanded,
  highlight,
  isLast,
  children,
  onClick,
}: {
  expandable: boolean;
  expanded: boolean;
  highlight: boolean;
  isLast: boolean;
  children: any;
  onClick: () => void;
}) {
  const classes: string[] = ["jstree-node"];

  if (expanded) {
    classes.push("jstree-open");
  } else {
    classes.push("jstree-closed");
  }

  if (!expandable) {
    classes.push("jstree-leaf");
  }

  if (isLast) {
    classes.push("jstree-last");
  }

  if (highlight) {
    children = <span className="jstree-clicked">{children}</span>;
  }

  return (
    <li role="treeitem" className={classes.join(" ")}>
      <i
        className="jstree-icon jstree-ocl"
        role="presentation"
        onClick={onClick}
      />
      {children}
    </li>
  );
}
