// Copyright (C) 2012 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.googlesource.gerrit.plugins.validators;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.inject.Singleton;
import org.eclipse.jgit.revwalk.RevCommit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Listen
@Singleton
public class CommitValidator implements CommitValidationListener {
  private static Logger log = LoggerFactory.getLogger(CommitValidator.class);

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
    throws CommitValidationException {
    final RevCommit commit = receiveEvent.commit;
    final String commitMessage = receiveEvent.commit.getFullMessage();
    final List<CommitValidationMessage> messages = new ArrayList<CommitValidationMessage>();

    if(commit.getShortMessage().length() >70){
      log.warn("CommitValidator> Commit "
          + receiveEvent.commit.getId().getName() + " REJECTED");
      throw new CommitValidationException("Commit length validation failed");
    }
    Matcher mModule = Pattern.compile("\\s?(Module|module)\\s?:\\s?").matcher(commitMessage);
    Matcher mProject = Pattern.compile("\\s?(Project|project)\\s?:\\s?").matcher(commitMessage);
    Matcher mType = Pattern.compile("\\s?(type|Type)\\s?:\\s?(style|feat|test|refactor|chore|fix|chore)").matcher(commitMessage);
    Matcher mTracnkingId = Pattern.compile("\\s?(Tracking-id|tracking-id|Tracking-Id)\\s?:\\s?").matcher(commitMessage);

    if ( ! mModule.find()) {
      log.warn("CommitValidator> Commit "
          + receiveEvent.commit.getId().getName() + " REJECTED");
      throw new CommitValidationException("Keyword 'module' commit messages not included");
    }
    if ( ! mProject.find()) {
      log.warn("CommitValidator> Commit "
          + receiveEvent.commit.getId().getName() + " REJECTED");
      throw new CommitValidationException("Keyword 'project' commit messages not included");
    }
    if (! mTracnkingId.find()) {
      log.warn("CommitValidator> Commit "
          + receiveEvent.commit.getId().getName() + " REJECTED");
      throw new CommitValidationException("Keyword 'tracking-id' commit messages not included");
    }
    if (! mType.find()) {
      log.warn("CommitValidator> Commit "
          + receiveEvent.commit.getId().getName() + " REJECTED");
      throw new CommitValidationException("Keyword 'type' commit messages not included and option in style|feat|test|refactor|chore|fix|chore");
    }
    log.info("CommitValidator> Commit "
        + receiveEvent.commit.getId().getName() + " ACCEPTED");
    return messages;
  }
}
