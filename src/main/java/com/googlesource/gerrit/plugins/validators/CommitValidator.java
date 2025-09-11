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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gerrit.extensions.annotations.Listen;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.inject.Singleton;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Gerrit 提交验证器
 * 
 * 验证提交消息格式，根据项目类型应用不同的验证规则：
 * - 只对评审推送分支 (refs/for/ 开头) 进行验证
 * - 非 driver 项目：需要包含 module、project、tracking-id、type 字段
 * - driver 项目：需要包含 Signed-off-by 字段
 */
@Listen
@Singleton
public class CommitValidator implements CommitValidationListener {
  
  private static final Logger log = LoggerFactory.getLogger(CommitValidator.class);
  
  // 常量定义
  private static final int MAX_COMMIT_MESSAGE_LENGTH = 100;
  private static final String DRIVER_PROJECT_KEYWORD = "driver";
  private static final String REFS_FOR_PREFIX = "refs/for/";
  
  // 正则表达式模式
  private static final Pattern MODULE_PATTERN = 
      Pattern.compile("(?i)^\\s*module\\s*:\\s*(\\S+)\\s*$", Pattern.MULTILINE);
  private static final Pattern PROJECT_PATTERN = 
      Pattern.compile("(?i)^\\s*project\\s*:\\s*(.+?)\\s*$", Pattern.MULTILINE);
  private static final Pattern TYPE_PATTERN = 
      Pattern.compile("(?i)^\\s*type\\s*:\\s*(style|feat|test|refactor|chore|fix)\\s*$", Pattern.MULTILINE);
  private static final Pattern TRACKING_ID_PATTERN = 
      Pattern.compile("(?i)^\\s*tracking-id\\s*:\\s*(NA|\\d+)\\s*$", Pattern.MULTILINE);
  private static final Pattern SIGNED_OFF_PATTERN = 
      Pattern.compile("^\\s*Signed-off-by:\\s*(.+?)\\s*$", Pattern.MULTILINE);

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {
    
    final RevCommit commit = receiveEvent.commit;
    final Project project = receiveEvent.project;
    final String commitMessage = commit.getFullMessage();
    final String commitId = commit.getId().getName();
    final String refsName = receiveEvent.command.getRefName(); 
    final List<CommitValidationMessage> messages = new ArrayList<>();

    log.info("开始验证提交: {} (分支: {})", commitId, refsName);

    // 1. 检查是否为评审推送分支 (refs/for/ 开头)
    if (!isReviewPush(refsName)) {
      log.info("跳过验证 - 非评审推送分支: {}", refsName);
      return messages;
    }

    try {
      // 2. 验证提交消息长度
      validateCommitMessageLength(commit, commitId);
      
      // 3. 根据项目类型进行不同的验证
      if (isDriverProject(project)) {
        log.info("权限仓库 - : {}", project);
        validateDriverProjectCommit(commitMessage, commitId);
      } else {
        log.info("权限仓库 - : {}", project);
        validateStandardProjectCommit(commitMessage, commitId);
      }
      
      log.info("提交验证通过: {} (分支: {})", commitId, refsName);
      return messages;
      
    } catch (CommitValidationException e) {
      log.warn("提交验证失败: {} (分支: {}) - {}", commitId, refsName, e.getMessage());
      throw e;
    }
  }

  /**
   * 验证提交消息长度
   */
  private void validateCommitMessageLength(RevCommit commit, String commitId) 
      throws CommitValidationException {
    if (commit.getShortMessage().length() > MAX_COMMIT_MESSAGE_LENGTH) {
      String errorMsg = String.format("提交消息长度超过限制 (%d 字符)", MAX_COMMIT_MESSAGE_LENGTH);
      throw new CommitValidationException(errorMsg);
    }
  }

  /**
   * 判断是否为评审推送分支 (refs/for/ 开头)
   */
  private boolean isReviewPush(String refsName) {
    return refsName != null && refsName.startsWith(REFS_FOR_PREFIX);
  }

  /**
   * 判断是否为 driver 项目
   */
  private boolean isDriverProject(Project project) {
    // 使用项目名称来判断是否为 driver 项目
    String projectName = project.getParent().get();
    return projectName != null && projectName.contains(DRIVER_PROJECT_KEYWORD);
  }

  /**
   * 验证标准项目的提交消息
   */
  private void validateStandardProjectCommit(String commitMessage, String commitId) 
      throws CommitValidationException {
    
    // 验证 module 字段
    if (!MODULE_PATTERN.matcher(commitMessage).find()) {
      throw new CommitValidationException(
          "缺少 'module' 字段或格式不正确。格式应为: Module: <模块名>");
    }
    
    // 验证 project 字段
    if (!PROJECT_PATTERN.matcher(commitMessage).find()) {
      throw new CommitValidationException(
          "缺少 'project' 字段或格式不正确。格式应为: Project: <项目名>");
    }
    
    // 验证 tracking-id 字段
    if (!TRACKING_ID_PATTERN.matcher(commitMessage).find()) {
      throw new CommitValidationException(
          "缺少 'tracking-id' 字段或格式不正确。格式应为: Tracking-id: <数字或NA>");
    }
    
    // 验证 type 字段
    if (!TYPE_PATTERN.matcher(commitMessage).find()) {
      throw new CommitValidationException(
          "缺少 'type' 字段或格式不正确。可选值: style|feat|test|refactor|chore|fix");
    }
  }

  /**
   * 验证 driver 项目的提交消息
   */
  private void validateDriverProjectCommit(String commitMessage, String commitId) 
      throws CommitValidationException {
    
    if (!SIGNED_OFF_PATTERN.matcher(commitMessage).find()) {
      throw new CommitValidationException(
          "缺少 'Signed-off-by' 字段。格式应为: Signed-off-by: <姓名> <邮箱>");
    }
  }
}
