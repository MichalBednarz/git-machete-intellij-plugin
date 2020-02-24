package com.virtuslab.gitcore.gitcorejgit;

import com.virtuslab.gitcore.gitcoreapi.IGitCorePersonIdentity;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.eclipse.jgit.lib.PersonIdent;

@EqualsAndHashCode
@Getter
public class JGitPersonIdentity implements IGitCorePersonIdentity {
  @Getter(AccessLevel.NONE)
  private final PersonIdent jgitPerson;

  private final String name;
  private final String email;

  public JGitPersonIdentity(PersonIdent person) {
    if (person == null)
      throw new NullPointerException("Person passed to PersonIdentity constructor cannot be null");
    this.jgitPerson = person;
    this.name = jgitPerson.getName();
    this.email = jgitPerson.getEmailAddress();
  }
}
