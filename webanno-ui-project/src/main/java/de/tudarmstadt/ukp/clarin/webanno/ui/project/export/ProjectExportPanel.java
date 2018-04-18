/*
 * Copyright 2012
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.ui.project.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.DownloadLink;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.wicketstuff.progressbar.ProgressBar;
import org.wicketstuff.progressbar.Progression;
import org.wicketstuff.progressbar.ProgressionModel;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ImportExportService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.constraints.ConstraintsService;
import de.tudarmstadt.ukp.clarin.webanno.export.ExportService;
import de.tudarmstadt.ukp.clarin.webanno.export.ExportUtil;
import de.tudarmstadt.ukp.clarin.webanno.export.ImportUtil;
import de.tudarmstadt.ukp.clarin.webanno.export.ProjectExportRequest;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.support.AJAXDownload;
import de.tudarmstadt.ukp.clarin.webanno.support.ZipUtils;
import de.tudarmstadt.ukp.clarin.webanno.support.logging.Logging;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.settings.ProjectSettingsPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.settings.ProjectSettingsPanelBase;
import de.tudarmstadt.ukp.clarin.webanno.ui.core.settings.ProjectSettingsPanelCondition;
import de.tudarmstadt.ukp.clarin.webanno.ui.project.ProjectPage;

/**
 * A Panel used to add Project Guidelines in a selected {@link Project}
 */
@ProjectSettingsPanel(label = "Export")
public class ProjectExportPanel
    extends ProjectSettingsPanelBase
{
    private static final long serialVersionUID = 2116717853865353733L;

    private static final Logger LOG = LoggerFactory.getLogger(ProjectPage.class);

    public static final String EXPORTED_PROJECT = ImportUtil.EXPORTED_PROJECT;

    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean DocumentService documentService;
    private @SpringBean ProjectService projectService;
    private @SpringBean ExportService exportService;
    private @SpringBean ImportExportService importExportService;
    private @SpringBean ConstraintsService constraintsService;
    private @SpringBean UserDao userRepository;

    private ProgressBar fileGenerationProgress;
    @SuppressWarnings("unused")
    private AjaxLink<Void> exportProjectLink;

    private String fileName;
    private String downloadedFile;
    @SuppressWarnings("unused")
    private String projectName;

    private transient Thread thread = null;
    private transient FileGenerator runnable = null;

    private boolean enabled = true;
    private boolean canceled = false;

    public ProjectExportPanel(String id, final IModel<Project> aProjectModel)
    {
        super(id, aProjectModel);
        add(new ProjectExportForm("exportForm", aProjectModel));
    }

    private boolean existsCurationDocument(Project aProject)
    {
        boolean curationDocumentExist = false;
        List<de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument> documents = documentService
                .listSourceDocuments(aProject);

        for (de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument sourceDocument : documents) {

            // If the curation document is finished
            if (SourceDocumentState.CURATION_FINISHED.equals(sourceDocument.getState())) {
                curationDocumentExist = true;
                break;
            }
        }
        return curationDocumentExist;
    }
    
    public class ProjectExportForm
        extends Form<ProjectExportRequest>
    {
        private static final long serialVersionUID = 9151007311548196811L;

        public ProjectExportForm(String id, IModel<Project> aProject)
        {
            super(id, new CompoundPropertyModel<>(
                    new ProjectExportRequest(aProject, ProjectExportRequest.FORMAT_AUTO)));
            
            add(new DropDownChoice<String>("format", new LoadableDetachableModel<List<String>>()
            {
                private static final long serialVersionUID = 1L;

                @Override
                protected List<String> load()
                {                    
                    List<String> formats = new ArrayList<>(
                            importExportService.getWritableFormatLabels());
                    formats.add(0, ProjectExportRequest.FORMAT_AUTO);
                    return formats;
                }
            }) {
                private static final long serialVersionUID = 1L;

                @Override
                protected boolean wantOnSelectionChangedNotifications()
                {
                    // Needed to update the model with the selection because the DownloadLink does
                    // not trigger a form submit.
                    return true;
                }
            });
            
            add(new DownloadLink("export", new LoadableDetachableModel<File>() {
                private static final long serialVersionUID = 840863954694163375L;

                @Override
                protected File load() {
                    File exportFile = null;
                    File exportTempDir = null;
                    try {
                        exportTempDir = File.createTempFile("webanno", "export");
                        exportTempDir.delete();
                        exportTempDir.mkdirs();

                        boolean curationDocumentExist = existsCurationDocument(
                                ProjectExportForm.this.getModelObject().project.getObject());

                        if (!curationDocumentExist) {
                            error("No curation document created yet for this document");
                        } else {
                            ExportUtil.exportCuratedDocuments(documentService, importExportService,
                                    ProjectExportForm.this.getModelObject(), exportTempDir, false);
                            ZipUtils.zipFolder(exportTempDir, new File(
                                    exportTempDir.getAbsolutePath() + ".zip"));
                            exportFile = new File(exportTempDir.getAbsolutePath()
                                    + ".zip");

                        }
                    }
                    catch (CASRuntimeException e) {
                        cancelOperationOnError();
                        error("Error: " + e.getMessage());
                    }
                    catch (Exception e) {
                        error("Error: " + e.getMessage());
                        cancelOperationOnError();
                    }
                    finally {
                        try {
                            FileUtils.forceDelete(exportTempDir);
                        } catch (IOException e) {
                            error("Unable to delete temp file");
                        }
                    }

                    return exportFile;
                }

                private void cancelOperationOnError()
                {
                    if (thread != null) {
                        ProjectExportForm.this.getModelObject().progress = 100;
                        thread.interrupt();
                    }
                }
            }, new LoadableDetachableModel<String>() {
                private static final long serialVersionUID = 2591915908792854707L;
//                Provide meaningful name to curated documents zip
                @Override
                protected String load()
                {
                    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HHmm");
                    return ProjectExportForm.this.getModelObject().project.getObject().getName() +
                        "_curated_documents_" + fmt.format(new Date()) + ".zip";
                }
            }) {
                private static final long serialVersionUID = 5630612543039605914L;

                @Override
                public boolean isVisible() {
                    return existsCurationDocument(ProjectExportForm.this
                            .getModelObject().project.getObject());
                }

                @Override
                public boolean isEnabled() {
                    return enabled;

                }
                
                @Override
                public void onClick()
                {
                    try {
                        super.onClick();
                    }
                    catch (IllegalStateException e) {
                        LOG.error("Error: {}", e.getMessage(), e);
                        error("Unable to export curated documents because of exception while processing.");
                    }
                }
            }.setDeleteAfterDownload(true)).setOutputMarkupId(true);

            final AJAXDownload exportProject = new AJAXDownload() {
                private static final long serialVersionUID = 2005074740832698081L;

                @Override
                protected String getFileName() {
                    String name;
                    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd_HHmm");
                    try {
                        name = URLEncoder.encode(ProjectExportForm.this.getModelObject().project
                                .getObject().getName(), "UTF-8");
                    }
                    catch (UnsupportedEncodingException e) {
                        name = super.getFileName();
                    }
                    
                    name = FilenameUtils.removeExtension(name);
                    name += "_" + fmt.format(new Date()) + ".zip";
                    
                    return name;
                }
            };

            fileGenerationProgress = new ProgressBar("progress", new ProgressionModel()
            {
                private static final long serialVersionUID = 1971929040248482474L;

                @Override
                protected Progression getProgression()
                {
                    return new Progression(ProjectExportForm.this.getModelObject().progress);
                }
            })
            {
                private static final long serialVersionUID = -6599620911784164177L;

                @Override
                protected void onFinished(AjaxRequestTarget target)
                {
                    if (!canceled && !fileName.equals(downloadedFile)) {
                        exportProject.initiate(target, fileName);
                        downloadedFile = fileName;
                        
                        while (!runnable.getMessages().isEmpty()) {
                            info(runnable.getMessages().poll());
                        }

                        enabled = true;
                        target.addChildren(getPage(), IFeedback.class);
                        info("Project export complete");
                    }
                    else if (canceled) {
                        enabled = true;
                        target.addChildren(getPage(), IFeedback.class);
                        info("Project export cancelled");
                    }
                }
            };

            fileGenerationProgress.add(exportProject);
            add(fileGenerationProgress);

            add(exportProjectLink = new AjaxLink<Void>("exportProject") {
                private static final long serialVersionUID = -5758406309688341664L;

                @Override
                public boolean isEnabled() {
                    return enabled;
                }

                @Override
                public void onClick(final AjaxRequestTarget target) {
                    enabled = false;
                    canceled = true;
                    ProjectExportForm.this.getModelObject().progress = 0;
                    target.add(ProjectExportPanel.this.getPage());
                    fileGenerationProgress.start(target);
                    Authentication authentication = SecurityContextHolder.getContext()
                            .getAuthentication();
                    runnable = new FileGenerator(ProjectExportForm.this.getModelObject(),
                            authentication.getName());
                    thread = new Thread(runnable);
                    thread.start();
                }
            });

            add(new AjaxLink<Void>("cancel") {
                private static final long serialVersionUID = 5856284172060991446L;

                @Override
                public void onClick(final AjaxRequestTarget target) {
                    if (thread != null) {
                        ProjectExportForm.this.getModelObject().progress = 100;
                        thread.interrupt();
                    }
                }

                @Override
                public boolean isEnabled()
                {
                    // Enabled only if the export button has been disabled (during export)
                    return (!enabled) ;
                }
            });
        }
    }
    
    public class FileGenerator
        implements Runnable
    {
        private String username;
        private ProjectExportRequest model;

        public FileGenerator(ProjectExportRequest aModel, String aUsername)
        {
            model = aModel;
            username = aUsername;
        }

        @Override
        public void run()
        {
            // We are in a new thread. Set up thread-specific MDC
            MDC.put(Logging.KEY_USERNAME, username);
            MDC.put(Logging.KEY_PROJECT_ID, String.valueOf(model.project.getObject().getId()));
            MDC.put(Logging.KEY_REPOSITORY_PATH, documentService.getDir().toString());
            
            File file;
            try {
                Thread.sleep(100); // Why do we sleep here?
                file = exportService.generateZipFile(model);
                fileName = file.getAbsolutePath();
                projectName = model.project.getObject().getName();
                canceled = false;
            }
            catch (FileNotFoundException e) {
                LOG.error("Unable to find some project file(s) during project export", e);
                model.messages.add("Unable to find file during project export: "
                        + ExceptionUtils.getRootCauseMessage(e));
            }
            catch (Throwable e) {
                LOG.error("Unexpected error during project export", e);
                model.messages.add("Unexpected error during project export: "
                        + ExceptionUtils.getRootCauseMessage(e));
                if (thread != null) {
                    canceled = true;
                    model.progress = 100;
                    thread.interrupt();
                }
            }
        }

        public Queue<String> getMessages()
        {
            return model.messages;
        }
    }
    
    @ProjectSettingsPanelCondition
    public static boolean settingsPanelCondition(Project aProject)
    {
        return true;
    }
}
