package com.virtuslab.gitcore.impl.jgit;

import java.io.IOException;
import java.nio.file.Path;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import com.virtuslab.gitcore.api.BaseGitCoreCommit;
import com.virtuslab.gitcore.api.GitCoreCannotAccessGitDirectoryException;
import com.virtuslab.gitcore.api.GitCoreException;
import com.virtuslab.gitcore.api.GitCoreNoSuchBranchException;
import com.virtuslab.gitcore.api.IGitCoreLocalBranch;
import com.virtuslab.gitcore.api.IGitCoreRemoteBranch;
import com.virtuslab.gitcore.api.IGitCoreRepository;

@Getter
@Slf4j(topic = "gitCore")
public class GitCoreRepository implements IGitCoreRepository {
  private final Repository jgitRepo;
  private final Git jgitGit;
  private final Path mainDirectoryPath;
  private final Path gitDirectoryPath;

  public GitCoreRepository(Path mainDirectoryPath, Path gitDirectoryPath) throws GitCoreException {
    log.debug(
        "Creating GitCoreRepository(/* mainDirectoryPath */ ${mainDirectoryPath}, /* gitDirectoryPath */ ${gitDirectoryPath})");
    this.mainDirectoryPath = mainDirectoryPath;
    this.gitDirectoryPath = gitDirectoryPath;

    jgitRepo = Try.of(() -> new FileRepository(gitDirectoryPath.toString())).getOrElseThrow(
        e -> new GitCoreCannotAccessGitDirectoryException("Cannot access .git directory under ${gitDirectoryPath}", e));
    jgitGit = new Git(jgitRepo);
  }

  @Override
  public Option<IGitCoreLocalBranch> getCurrentBranch() throws GitCoreException {
    Ref ref;
    try {
      ref = jgitRepo.getRefDatabase().findRef(Constants.HEAD);
    } catch (IOException e) {
      throw new GitCoreException("Cannot get current branch", e);
    }
    if (ref == null) {
      throw new GitCoreException("Error occurred while getting current branch ref");
    }
    if (ref.isSymbolic()) {
      return Option.of(new GitCoreLocalBranch(this, Repository.shortenRefName(ref.getTarget().getName())));
    }
    return Option.none();
  }

  @Override
  public GitCoreLocalBranch getLocalBranch(String branchName) throws GitCoreException {
    if (isBranchMissing(GitCoreLocalBranch.BRANCHES_PATH + branchName)) {
      throw new GitCoreNoSuchBranchException("Local branch '${branchName}' does not exist in this repository");
    }
    return new GitCoreLocalBranch(/* repo */ this, branchName);
  }

  @Override
  public GitCoreRemoteBranch getRemoteBranch(String branchName) throws GitCoreException {
    if (isBranchMissing(GitCoreRemoteBranch.BRANCHES_PATH + branchName)) {
      throw new GitCoreNoSuchBranchException("Remote branch '${branchName}' does not exist in this repository");
    }
    return new GitCoreRemoteBranch(/* repo */ this, branchName);
  }

  @Override
  public List<IGitCoreLocalBranch> getLocalBranches() throws GitCoreException {
    log.debug("Enter getLocalBranches for repository ${mainDirectoryPath} (${gitDirectoryPath})");
    log.debug("List of local branches branches:");
    return Try.of(() -> getJgitGit().branchList().call())
        .getOrElseThrow(e -> new GitCoreException("Error while getting list of local branches", e))
        .stream()
        .filter(branch -> !branch.getName().equals(Constants.HEAD))
        .peek(branch -> log.debug("* ${branch.getName()}"))
        .map(ref -> new GitCoreLocalBranch(/* repo */ this,
            ref.getName().replace(GitCoreLocalBranch.BRANCHES_PATH, /* replacement */ "")))
        .collect(List.collector());
  }

  @Override
  public List<IGitCoreRemoteBranch> getRemoteBranches() throws GitCoreException {
    log.debug("Enter getRemoteBranches for repository ${mainDirectoryPath} (${gitDirectoryPath})");
    log.debug("List of remote branches branches:");
    return Try.of(() -> getJgitGit().branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call())
        .getOrElseThrow(e -> new GitCoreException("Error while getting list of remote branches", e))
        .stream()
        .filter(branch -> !branch.getName().equals(Constants.HEAD))
        .peek(branch -> log.debug("* ${branch.getName()}"))
        .map(ref -> new GitCoreRemoteBranch(/* repo */ this,
            ref.getName().replace(GitCoreRemoteBranch.BRANCHES_PATH, /* replacement */ "")))
        .collect(List.collector());
  }

  private boolean isBranchMissing(String branchName) throws GitCoreException {
    return Try.of(() -> Option.of(jgitRepo.resolve(branchName)))
        .getOrElseThrow(e -> new GitCoreException(e))
        .isEmpty();
  }

  private ObjectId toExistingObjectId(BaseGitCoreCommit c) throws GitCoreException {
    try {
      ObjectId result = jgitRepo.resolve(c.getHash().getHashString());
      assert result != null : "Invalid commit ${c}";
      return result;
    } catch (IOException e) {
      throw new GitCoreException(e);
    }
  }

  @Nullable
  private RevCommit deriveMergeBase(BaseGitCoreCommit c1, BaseGitCoreCommit c2) throws GitCoreException {
    log.debug("Enter deriveMergeBase for repository ${mainDirectoryPath} (${gitDirectoryPath})");
    RevWalk walk = new RevWalk(jgitRepo);
    walk.setRevFilter(RevFilter.MERGE_BASE);
    try {
      walk.markStart(walk.parseCommit(toExistingObjectId(c1)));
      walk.markStart(walk.parseCommit(toExistingObjectId(c2)));
      RevCommit mergeBase = walk.next();
      log.debug(
          "Detected merge base for ${c1.getHash().getHashString()} and ${c2.getHash().getHashString()} is ${mergeBase}");
      return mergeBase;
    } catch (IOException e) {
      throw new GitCoreException(e);
    }
  }

  private final java.util.Map<Tuple2<BaseGitCoreCommit, BaseGitCoreCommit>, @Nullable GitCoreCommitHash> mergeBaseCache = new java.util.HashMap<>();

  @Nullable
  private GitCoreCommitHash deriveMergeBaseIfNeeded(BaseGitCoreCommit a, BaseGitCoreCommit b) throws GitCoreException {
    log.debug("Enter deriveMergeBaseIfNeeded for ${a} and ${b}");
    var abKey = Tuple.of(a, b);
    var baKey = Tuple.of(b, a);
    if (mergeBaseCache.containsKey(abKey)) {
      log.debug("Merge base for ${a.getHash().getHashString()} and ${b.getHash().getHashString()} found in cache");
      return mergeBaseCache.get(abKey);
    } else if (mergeBaseCache.containsKey(baKey)) {
      log.debug("Merge base for ${b.getHash().getHashString()} and ${a.getHash().getHashString()} found in cache");
      return mergeBaseCache.get(baKey);
    } else {
      var mergeBase = deriveMergeBase(a, b);
      GitCoreCommitHash mergeBaseHash = mergeBase != null ? GitCoreCommitHash.of(mergeBase) : null;
      mergeBaseCache.put(abKey, mergeBaseHash);
      return mergeBaseHash;
    }
  }

  @Override
  public boolean isAncestor(BaseGitCoreCommit presumedAncestor, BaseGitCoreCommit presumedDescendant)
      throws GitCoreException {
    log.debug(
        "Enter isAncestor for presumedAncestor = ${presumedAncestor.getHash().getHashString()} and presumedDescendant = ${presumedDescendant.getHash().getHashString()}");

    if (presumedAncestor.equals(presumedDescendant)) {
      log.debug("presumedAncestor is equal to presumedDescendant");
      return true;
    }
    var mergeBaseHash = deriveMergeBaseIfNeeded(presumedAncestor, presumedDescendant);
    if (mergeBaseHash == null) {
      log.debug(
          "merge base of presumedAncestor and presumedDescendant not found => presumedAncestor is not ancestor of presumedDescendant");
      return false;
    }
    boolean isAncestor = mergeBaseHash.equals(presumedAncestor.getHash());
    log.debug(
        "merge base of presumedAncestor and presumedDescendant is equal to presumedAncestor => presumedAncestor is ancestor of presumedDescendant");
    return isAncestor;
  }
}
