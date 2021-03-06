package model.modtree;

import java.util.logging.Level;
import java.util.logging.Logger;
import static model.modtree.ModContext.ModContextType.AFTER_HEX;
import static model.modtree.ModContext.ModContextType.BEFORE_HEX;
import static model.modtree.ModContext.ModContextType.FILE_HEADER;
import static model.modtree.ModContext.ModContextType.HEX_CODE;
import static model.modtree.ModContext.ModContextType.HEX_HEADER;
import static model.modtree.ModContext.ModContextType.VALID_CODE;
import model.modtree.ModContext.ModContextType;

/**
 *
 * @author Amineri
 */
public class ModTreeRootNode extends ModTreeNode {
	
	/**
	 * The reference to the document instance. This root node is the only node
	 * in the hierarchy actually storing this reference.
	 */
	private final ModTree tree;
	
    /**
     * The persistent global context maintained while parsing through the file.
     */
//    private ModContext globalContext;

	/**
	 * 
	 * @param tree
	 */
	public ModTreeRootNode(ModTree tree) {
		super(null);
		this.tree = tree;

		// add initial hierarchy
		ModTreeNode child = new ModTreeNode(this, true);
		child.addNode(new ModTreeLeaf(child, "", true));
		this.addNode(child);
	}
    
	/**
	 * Splits multi-line tokens into single Line tokens.<br>
	 * Re-computes contexts for all elements and tokens.<br>
	 * Adds line-split text into tree structure
	 * Re-parses any modified unrealhex.
	 * @param text the text to set
	 */
	@Override
	public void setText(String text) {
		this.resetContextFlags();
		this.splitLinesAndAddToTree(text);
		//this.splitElementsOnNewline();
		this.buildContextsParseUnreal();
		this.setMemoryPositions();
	}

	private void splitLinesAndAddToTree(String fullText) {
        // iterate through array of lines and break into lines
		//String fullText = this.getFullText();
		if (!fullText.isEmpty()) {
			//this.removeAllChildNodes();
			//this.removeChildNodeAt(0);
			fullText = fullText.replace("\r", "");
			String[] lines = fullText.split("\n");
			int startOffset = 0;
			ModTreeNode newLine;
			ModTreeLeaf newLeaf, newLeafComment;
			for(String line : lines) {
				line += "\n";				
				
				String[] split = line.split("//", 2);
				if (split.length > 1) {
					// line contains comment
					String pre = split[0];
					String post = "//" + split[1];

					newLine = new ModTreeNode(this, true);
					newLeaf = new ModTreeLeaf(newLine, pre, true);
					newLine.addNode(newLeaf);
					newLeafComment = new ModTreeLeaf(newLine, post, true);
					newLine.addNode(newLeafComment);

					newLeaf.setRange(startOffset, pre.length());
					newLeafComment.setRange(startOffset + pre.length(), startOffset + line.length());
					
				} else {
					newLine = new ModTreeNode(this, true);
					newLeaf = new ModTreeLeaf(newLine, line, true);
					newLine.addNode(newLeaf);
					newLeaf.setRange(startOffset, startOffset + line.length());
				}
				newLine.setRange(startOffset, startOffset + line.length());
				startOffset += line.length();
				
				this.addNode(newLine);
			}
		}
	}
	

	private void splitElementsOnNewline() {
        // iterate through array of lines and break into lines
        int index = 0;
        int childCount = this.getChildNodeCount();
        do {
        	ModTreeNode child = this.getChildNodeAt(index);
        	index ++;
			if ((index <= childCount) && child.isPlainText()) {
				String fullText = child.getFullText();
				if (!fullText.isEmpty() && fullText.contains("\n")) {
					fullText = fullText.replace("\r", "");
					String[] strings = fullText.split("\n", 2);
					if (!strings[1].isEmpty()) {
						strings[0] += "\n";
						
						int oldStartOffset = child.getStartOffset();
						int oldEndOffset = child.getEndOffset();
						int newEndOffset = oldStartOffset + strings[0].length();
						
						ModTreeNode grandChild = child.getChildNodeAt(0);
						
    					String[] split = strings[0].split("//", 2);
    					if (split.length > 1) {
    						// line contains comment
    						String pre = split[0];
    						String post = "//" + split[1];
    						
    						int splitOffset = newEndOffset - post.length();
    						
    						grandChild.setText(pre);
							grandChild.setRange(oldStartOffset, splitOffset);
    						
    						ModTreeLeaf grandChildSibling = new ModTreeLeaf(child, post, true);
    						child.addNode(grandChildSibling);
    						grandChildSibling.setRange(splitOffset, newEndOffset);
    					} else {
    						grandChild.setText(strings[0]);
    						grandChild.setRange(oldStartOffset, newEndOffset);
    					}
						
						child.setRange(oldStartOffset, newEndOffset);

						ModTreeNode newElement = new ModTreeNode(this, true);
//							newElement.setUpdateFlag(true);
						
						ModTreeLeaf newToken = new ModTreeLeaf(newElement, strings[1], true);
//    							newToken.setUpdateFlag(true);
						newToken.setRange(newEndOffset, oldEndOffset);

						newElement.addNode(newToken);
						newElement.setRange(newEndOffset, oldEndOffset);
						
						this.addNode(index, newElement);
						childCount++;
					}
				}
        	}
        } while(index < childCount); 
    }

    /**
     * Glues together lines not separated by newlines.
     * Re-computes contexts for all elements and tokens.
     * Re-parses any modified unrealhex.
     */
    public void reorganizeAfterDeletion()
    {
        this.resetContextFlags();
        this.glueElementsWithoutNewline();
        this.buildContextsParseUnreal();
		this.setMemoryPositions();
    }

	private void glueElementsWithoutNewline() {
		// iterate through array of lines and break into lines
		int count = 0;
		int numbranches = this.getChildNodeCount();
		do {
			ModTreeNode branch = this.getChildNodeAt(count);
			if (branch.isPlainText()) {
				if (!branch.getFullText().isEmpty()) {
					if (!branch.getFullText().contains("\n") && count + 1 < numbranches) {
						numbranches--;
						String gluedString = branch.getFullText()
								+ this.getChildNodeAt(count + 1).getFullText();
						ModTreeNode branchBranch = branch.getChildNodeAt(0);
						branchBranch.setText(gluedString);
						branch.setRange(branch.getStartOffset(),branch.getStartOffset() + gluedString.length());
						branchBranch.setRange(branchBranch.getStartOffset(),branchBranch.getStartOffset() + gluedString.length());
//						branch.setUpdateFlag(true);
//						branchBranch.setUpdateFlag(true);
						this.removeChildNodeAt(count + 1);
					} else {
						count++;
					}
				} else { // handle empty string removal
					if (tree.getRoot().getEndOffset() == 0) {
						// if document is empty exit out
						count++;
					} else {
						// otherwise remove the element with the empty leaf
						numbranches--;
						this.removeChildNodeAt(count);
					}
				}
			} else {
				count++;
			}
		} while (count < numbranches);
	}

	private void buildContextsParseUnreal() {
        // iterate through array of lines 
		for (int i = 0; i < this.getChildNodeCount(); i++) {
			ModTreeNode child = this.getChildNodeAt(i);
			
            // update contexts
            child.updateContexts();
			
			//store copy of global contexts at current child
			child.setContextFlag(FILE_HEADER, this.getContextFlag(FILE_HEADER));
			child.setContextFlag(BEFORE_HEX, this.getContextFlag(BEFORE_HEX));
			child.setContextFlag(AFTER_HEX, this.getContextFlag(AFTER_HEX));
			child.setContextFlag(HEX_HEADER, this.getContextFlag(HEX_HEADER));
			child.setContextFlag(HEX_CODE, this.getContextFlag(HEX_CODE));
			child.setContextFlag(VALID_CODE, this.getContextFlag(VALID_CODE));
            
            //  consolidate/expand code lines
            boolean isCode = child.getContextFlag(ModContextType.HEX_CODE);
			if (!isCode && !child.isPlainText()) {	// consolidate string
                ModTreeLeaf leaf = new ModTreeLeaf(child, child.getFullText(), true);
                leaf.setRange(child.getStartOffset(), child.getEndOffset());
                child.removeAllChildNodes();
                child.addNode(leaf);
                child.setPlainText(true);
            }
            if (isCode && child.isPlainText()) {
				try {
					child.parseUnrealHex(null, 0);
				} catch (Exception ex) {
					//Logger.getLogger(ModTreeRootNode.class.getName()).log(Level.SEVERE, "Unreal hex parsing failed at line " + i, ex);
				}
            }
		}
    }
    
    @Override
    public String getName() {
    	return "ModRootElement";
    }
	
	@Override
	public ModTree getTree() {
		return this.tree;
	}

	@Override
	protected ModTreeNode getRoot()
	{
		return this;
	}

	/**
	 * Resets the context flags to their default values (<code>false</code>).
	 * All flags set to false except for FILE_HEADER = true.
	 * Reset file attributes in case they were changed.
	 */
	@Override
	public void resetContextFlags() {
		super.resetContextFlags();
		this.setContextFlag(FILE_HEADER, true);
		
		ModTree aTree = this.getTree();
		if (aTree != null) {
			aTree.setFileVersion(-1);
			aTree.setUpkName("");
			aTree.setGuid("");
			aTree.setFunctionName("");
			aTree.setResizeAmount(0);
		}
	}

//	@Override
//	public void setText(String text) {
//		// do nothing
//	}

//	@Override
//	public String getText() {
//		// return nothing
//		return "";
//	}

	/**
	 * Sets memory positions for each line 
	 */
	protected void setMemoryPositions() {
		int currMemoryPosition = 0;
		int currFilePosition = 0;
		for (int i = 0; i < this.getChildNodeCount(); i++) {
			// store current positions
			this.getChildNodeAt(i).setMemoryPosition(currMemoryPosition);
			this.getChildNodeAt(i).setFilePosition(currFilePosition);
			// increment position with current line size
			currMemoryPosition += this.getChildNodeAt(i).getMemorySize();
			currFilePosition += this.getChildNodeAt(i).getFileSize();
			// if not valid hex code reset the position
			if(!this.getChildNodeAt(i).getContextFlag(HEX_CODE)) {
				currMemoryPosition = 0;
				currFilePosition = 0;
			}
		}
	}
	
}
