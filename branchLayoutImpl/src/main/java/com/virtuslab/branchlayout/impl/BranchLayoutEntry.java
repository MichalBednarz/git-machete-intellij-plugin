package com.virtuslab.branchlayout.impl;

import java.util.Optional;

import io.vavr.collection.List;

import lombok.Data;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.virtuslab.branchlayout.api.IBranchLayoutEntry;

@Data
public class BranchLayoutEntry implements IBranchLayoutEntry {
  private final String name;
  @Nullable
  private final String customAnnotation;
  private final List<IBranchLayoutEntry> subbranches;

  @Override
  public Optional<String> getCustomAnnotation() {
    return Optional.ofNullable(customAnnotation);
  }
}