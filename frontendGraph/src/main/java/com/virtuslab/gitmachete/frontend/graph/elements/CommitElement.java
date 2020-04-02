package com.virtuslab.gitmachete.frontend.graph.elements;

import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.virtuslab.gitmachete.backend.api.IGitMacheteCommit;
import com.virtuslab.gitmachete.frontend.graph.coloring.GraphEdgeColor;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class CommitElement extends BaseGraphElement {
  private final IGitMacheteCommit commit;
  private final int branchElementIndex;

  public CommitElement(
      IGitMacheteCommit commit,
      int upElementIndex,
      int downElementIndex,
      int branchElementIndex,
      GraphEdgeColor containingBranchGraphEdgeColor) {
    super(upElementIndex, containingBranchGraphEdgeColor);
    this.commit = commit;
    this.branchElementIndex = branchElementIndex;
    getDownElementIndexes().add(downElementIndex);
  }

  @Override
  public String getValue() {
    return commit.getMessage();
  }

  @Override
  public SimpleTextAttributes getAttributes() {
    return new SimpleTextAttributes(SimpleTextAttributes.STYLE_ITALIC | SimpleTextAttributes.STYLE_SMALLER,
        UIUtil.getInactiveTextColor());
  }
}