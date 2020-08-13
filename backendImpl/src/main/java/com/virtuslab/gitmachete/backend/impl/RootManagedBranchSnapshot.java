package com.virtuslab.gitmachete.backend.impl;

import io.vavr.collection.List;
import lombok.CustomLog;
import lombok.ToString;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.virtuslab.gitmachete.backend.api.ICommitOfManagedBranch;
import com.virtuslab.gitmachete.backend.api.IRemoteBranchReference;
import com.virtuslab.gitmachete.backend.api.IRootManagedBranchSnapshot;
import com.virtuslab.gitmachete.backend.api.SyncToRemoteStatus;

@CustomLog
@ToString
public final class RootManagedBranchSnapshot extends BaseManagedBranchSnapshot implements IRootManagedBranchSnapshot {

  public RootManagedBranchSnapshot(
      String name,
      String fullName,
      List<NonRootManagedBranchSnapshot> children,
      ICommitOfManagedBranch pointedCommit,
      @Nullable IRemoteBranchReference remoteTrackingBranch,
      SyncToRemoteStatus syncToRemoteStatus,
      @Nullable String customAnnotation,
      @Nullable String statusHookOutput) {
    super(name, fullName, children, pointedCommit, remoteTrackingBranch, syncToRemoteStatus, customAnnotation,
        statusHookOutput);

    LOG.debug("Creating ${this}");

    // Note: since the class is final, `this` is already @Initialized at this point.
    setParentForChildren();
  }
}