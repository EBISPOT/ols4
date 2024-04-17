package uk.ac.ebi.spot.ols.repository.v1;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

public class TreeNode<T> implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -343190255910189166L;
	private Collection<TreeNode<T>> children = new ArrayList<TreeNode<T>>();
	private Collection<TreeNode<T>> related = new ArrayList<TreeNode<T>>();
    private Collection<TreeNode<T>> parent = new ArrayList<TreeNode<T>>();
    private String index;
    private T data = null;
    
    public TreeNode(T data) {
        this.data = data;
    }
    
    public TreeNode(T data, Collection<TreeNode<T>> parent) {
        this.data = data;
        this.parent = parent;
    }

	public Collection<TreeNode<T>> getChildren() {
		return children;
	}
	public void setChildren(Collection<TreeNode<T>> children) {
		this.children = children;
	}
	
    public void addChild(T data) {
        TreeNode<T> child = new TreeNode<T>(data);
        this.children.add(child);
    }

    public void addChild(TreeNode<T> child) {
        this.children.add(child);
    }
    
    public void addRelated(T data) {
        TreeNode<T> related = new TreeNode<T>(data);
        this.related.add(related);
    }

    public void addRelated(TreeNode<T> related) {
        this.related.add(related);
    }
    
    public void addParent(T data) {
        TreeNode<T> parent = new TreeNode<T>(data);
        this.parent.add(parent);
    }

    public void addParent(TreeNode<T> parent) {
        this.parent.add(parent);
    }
	
	public Collection<TreeNode<T>> getRelated() {
		return related;
	}
	public void setRelated(Collection<TreeNode<T>> related) {
		this.related = related;
	}
	public Collection<TreeNode<T>>  getParent() {
		return parent;
	}
	public void setParent(Collection<TreeNode<T>>  parent) {
		this.parent = parent;
	}
	public String getIndex() {
		return index;
	}
	public void setIndex(String index) {
		this.index = index;
	}
	
    public T getData() {
        return this.data;
    }

    public void setData(T data) {
        this.data = data;
    }
	
   public boolean isRoot() {
        return this.parent.size() == 0;
    }

    public boolean isLeaf() {
        return this.children.size() == 0;
    }

    public void resetParent() {
        this.parent = new ArrayList<TreeNode<T>>();
    }
    
    public void resetChildren() {
        this.children = new ArrayList<TreeNode<T>>();
    }
    
    public void resetRelated() {
        this.related = new ArrayList<TreeNode<T>>();
    }
}
