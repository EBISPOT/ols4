import { hotkeysCoreFeature, syncDataLoaderFeature, type ItemInstance } from "@headless-tree/core";
import { useTree } from "@headless-tree/react";
import { useMemo, type ReactNode } from "react";
import type { TreeNode } from "../ontologiesSlice";

const ROOT = "ols-tree-root";
const features = [syncDataLoaderFeature, hotkeysCoreFeature];

type NodeItem = ItemInstance<TreeNode | undefined>;

interface OntologyTreeViewProps {
  roots: TreeNode[];
  childrenByPath: Record<string, TreeNode[]>;
  requiredByPath: Record<string, TreeNode[]>;
  expandedItems: string[];
  selectedIri?: string;
  isWide: (node: TreeNode) => boolean;
  setExpanded: (node: TreeNode, expanded: boolean) => void;
  activate: (node: TreeNode) => void;
  renderLabel: (node: TreeNode) => ReactNode;
  renderDetails: (node: TreeNode) => ReactNode;
}

function compareNodes(a: TreeNode, b: TreeNode) {
  const aIsIndividual = a.entity.getType() === "individual";
  const bIsIndividual = b.entity.getType() === "individual";
  if (aIsIndividual !== bIsIndividual) return aIsIndividual ? 1 : -1;
  const aTitle = a.title.toUpperCase();
  const bTitle = b.title.toUpperCase();
  return aTitle === bTitle ? 0 : aTitle > bTitle ? 1 : -1;
}

export default function OntologyTreeView({
  roots, childrenByPath, requiredByPath, expandedItems, selectedIri,
  isWide, setExpanded, activate, renderLabel, renderDetails,
}: OntologyTreeViewProps) {
  const nodes = useMemo(() => {
    const byPath = new Map<string, TreeNode>();
    for (const group of [roots, ...Object.values(childrenByPath), ...Object.values(requiredByPath)]) {
      for (const node of group) byPath.set(node.absoluteIdentity, node);
    }
    return byPath;
  }, [roots, childrenByPath, requiredByPath]);

  const tree = useTree<TreeNode | undefined>({
    rootItemId: ROOT,
    state: { expandedItems },
    setExpandedItems: (update) => {
      const previous = new Set(expandedItems);
      const next = new Set(typeof update === "function" ? update(expandedItems) : update);
      for (const id of Array.from(new Set([...expandedItems, ...Array.from(next)]))) {
        const node = nodes.get(id);
        if (node && previous.has(id) !== next.has(id)) setExpanded(node, next.has(id));
      }
    },
    getItemName: (item) => item.getItemData()?.title || "",
    isItemFolder: (item) => !!item.getItemData()?.expandable,
    onPrimaryAction: (item) => {
      const node = item.getItemData();
      if (node) activate(node);
    },
    hotkeys: {
      customOpenTerm: {
        hotkey: "Enter",
        preventDefault: true,
        isEnabled: (instance) => instance.getFocusedItem()?.getElement() === document.activeElement,
        handler: (_, instance) => instance.getFocusedItem()?.primaryAction(),
      },
    },
    dataLoader: {
      getItem: (id) => nodes.get(id),
      getChildren: (id) => {
        const parent = nodes.get(id);
        const children = id === ROOT ? roots : (parent && isWide(parent) ? requiredByPath[id] : childrenByPath[id]) || [];
        // Keep a repeated term on other parent paths, but omit cycles on this path.
        const ancestors = new Set(id === ROOT ? [] : id.split(";"));
        const ordered = children.filter(child => !ancestors.has(child.iri)).sort(compareNodes);
        return Array.from(new Set(ordered.map(child => child.absoluteIdentity)));
      },
    },
    features,
  });

  const visibleChildren = useMemo(() => {
    // The loader is synchronous, so rebuild before reading when OLS data changes.
    tree.scheduleRebuildTree();
    const byParent = new Map<string, NodeItem[]>();
    for (const item of tree.getItems()) {
      const { parentId } = item.getItemMeta();
      const children = byParent.get(parentId) || [];
      children.push(item);
      byParent.set(parentId, children);
    }
    return byParent;
  }, [tree, nodes, isWide, expandedItems]);

  function renderItem(item: NodeItem) {
    const node = item.getItemData();
    if (!node) return null;
    const children = visibleChildren.get(item.getId()) || [];
    const expanded = item.isExpanded();
    const details = expanded ? renderDetails(node) : null;
    return (
      <div
        {...item.getProps()}
        key={item.getId()}
        aria-current={node.iri === selectedIri ? "true" : undefined}
        aria-selected={undefined}
        className="ols-tree-item"
        onFocus={(event) => {
          if (event.target === event.currentTarget && !item.isFocused()) item.setFocused();
        }}
        onClick={(event) => {
          if (event.target instanceof Element && event.target.closest('[role="treeitem"]') === event.currentTarget) {
            item.setFocused();
            tree.updateDomFocus();
          }
        }}
      >
        <div className="ols-tree-label">
          {item.isFolder() ? (
            <button
              type="button"
              tabIndex={-1}
              className="ols-tree-toggle"
              aria-label={(expanded ? "Collapse " : "Expand ") + node.title}
              onClick={() => expanded ? item.collapse() : item.expand()}
            >
              {expanded ? "▾" : "▸"}
            </button>
          ) : <span className="ols-tree-toggle" aria-hidden="true" />}
          <span className="ols-tree-caption">{renderLabel(node)}</span>
        </div>
        {children.length > 0 && (
          <div role="group" className="ols-tree-children">{children.map(renderItem)}</div>
        )}
        {/* The selected path belongs between the branch label and its table. */}
        {details && (
          <div
            className="ols-tree-details"
            onClick={event => event.stopPropagation()}
            onKeyDown={event => event.stopPropagation()}
          >
            {details}
          </div>
        )}
      </div>
    );
  }

  return (
    <div {...tree.getContainerProps("Ontology terms")} className="ols-tree">
      {visibleChildren.get(ROOT)?.map(renderItem)}
    </div>
  );
}
