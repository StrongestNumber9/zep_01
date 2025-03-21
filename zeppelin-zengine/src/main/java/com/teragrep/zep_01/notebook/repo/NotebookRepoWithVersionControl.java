/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.teragrep.zep_01.notebook.repo;

import org.apache.commons.lang3.StringUtils;
import com.teragrep.zep_01.annotation.ZeppelinApi;
import com.teragrep.zep_01.notebook.Note;
import com.teragrep.zep_01.user.AuthenticationInfo;

import java.io.IOException;
import java.util.List;

/**
 * Notebook repository (persistence layer) abstraction
 */
public interface NotebookRepoWithVersionControl extends NotebookRepo {

  /**
   * chekpoint (set revision) for notebook.
   * @param noteId Id of the note
   * @param notePath path of the note
   * @param checkpointMsg message description of the checkpoint
   * @return Rev
   * @throws IOException
   */
  @ZeppelinApi Revision checkpoint(String noteId,
                                   String notePath,
                                   String checkpointMsg,
                                   AuthenticationInfo subject) throws IOException;

  /**
   * Get particular revision of the Notebook.
   *
   * @param noteId Id of the note
   * @param notePath path of the note
   * @param revId revision of the Notebook
   * @return a Notebook
   * @throws IOException
   */
  @ZeppelinApi Note get(String noteId, String notePath, String revId, AuthenticationInfo subject)
      throws IOException;

  /**
   * List of revisions of the given Notebook.
   *
   * @param noteId id of the note
   * @param notePath path of the note
   * @param subject
   * @return list of revisions
   */
  @ZeppelinApi List<Revision> revisionHistory(String noteId,
                                              String notePath,
                                              AuthenticationInfo subject) throws IOException;

  /**
   * Set note to particular revision.
   *
   * @param noteId Id of the Notebook
   * @param notePath path of the note
   * @param revId revision of the Notebook
   * @return a Notebook
   * @throws IOException
   */
  @ZeppelinApi Note setNoteRevision(String noteId, String notePath, String revId,
                                    AuthenticationInfo subject) throws IOException;

  /**
   * Represents the 'Revision' a point in life of the notebook
   */
  class Revision {
    public static final Revision EMPTY = new Revision(StringUtils.EMPTY, StringUtils.EMPTY, 0);

    public String id;
    public String message;
    public int time;

    public Revision(String revId, String message, int time) {
      this.id = revId;
      this.message = message;
      this.time = time;
    }

    public static boolean isEmpty(Revision revision) {
      return revision == null || EMPTY.equals(revision);
    }
  }

}
