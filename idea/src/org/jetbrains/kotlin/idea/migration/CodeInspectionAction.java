/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.migration;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.application.options.schemes.SchemesCombo;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.profile.codeInspection.ui.header.InspectionToolsConfigurable;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;

import static org.jetbrains.kotlin.idea.migration.KotlinMigrationProfileKt.createMigrationProfile;

public class CodeInspectionAction extends BaseAnalysisAction {
    private GlobalInspectionContextImpl myGlobalInspectionContext;
    private InspectionProfileImpl myExternalProfile;

    public CodeInspectionAction(String title, String analysisNoon) {
        super(title, analysisNoon);
    }

    @Override
    protected void analyze(@NotNull Project project, @NotNull AnalysisScope scope) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("codeassist.inspect.batch");
        try {
            runInspections(project, scope);
        }
        finally {
            myGlobalInspectionContext = null;
            myExternalProfile = null;
        }
    }

    private void runInspections(Project project, AnalysisScope scope) {
        scope.setSearchInLibraries(false);

        FileDocumentManager.getInstance().saveAllDocuments();

        GlobalInspectionContextImpl inspectionContext = getGlobalInspectionContext(project);
        inspectionContext.setExternalProfile(myExternalProfile);
        inspectionContext.setCurrentScope(scope);
        inspectionContext.doInspections(scope);
    }

    private GlobalInspectionContextImpl getGlobalInspectionContext(Project project) {
        if (myGlobalInspectionContext == null) {
            myGlobalInspectionContext = ((InspectionManagerEx) InspectionManager.getInstance(project)).createNewGlobalContext(false);
        }
        return myGlobalInspectionContext;
    }

    @Override
    @NonNls
    protected String getHelpTopic() {
        return "reference.dialogs.inspection.scope";
    }

    @Override
    protected void canceled() {
        super.canceled();
        myGlobalInspectionContext = null;
    }

    @Override
    protected JComponent getAdditionalActionSettings(@NotNull Project project, BaseAnalysisActionDialog dialog) {
        InspectionManagerEx manager = (InspectionManagerEx) InspectionManager.getInstance(project);

        CodeInspectionAction.AdditionalPanel panel = new CodeInspectionAction.AdditionalPanel();

        @SuppressWarnings("unchecked")
        SchemesCombo<InspectionProfileImpl> profiles = (SchemesCombo<InspectionProfileImpl>) panel.myBrowseProfilesCombo.getComboBox();

        InspectionProfileManager profileManager = InspectionProfileManager.getInstance();
        ProjectInspectionProfileManager projectProfileManager = ProjectInspectionProfileManager.getInstance(project);
        panel.myBrowseProfilesCombo.addActionListener(e -> {
            ExternalProfilesComboboxAwareInspectionToolsConfigurable errorConfigurable =
                    createConfigurable(projectProfileManager, profiles);

            MySingleConfigurableEditor editor = new MySingleConfigurableEditor(project, errorConfigurable, manager);
            if (editor.showAndGet()) {
                reloadProfiles(profiles, project);
                if (errorConfigurable.mySelectedName != null) {
                    InspectionProfileImpl profile =
                            (errorConfigurable.mySelectedIsProjectProfile ? projectProfileManager : profileManager)
                                    .getProfile(errorConfigurable.mySelectedName);
                    profiles.selectScheme(profile);
                }
            }
            else {
                //if profile was disabled and cancel after apply was pressed
                InspectionProfile profile = profiles.getSelectedScheme();
                boolean canExecute = profile != null && profile.isExecutable(project);
                dialog.setOKActionEnabled(canExecute);
            }
        });

        profiles.addActionListener(e -> {
            myExternalProfile = profiles.getSelectedScheme();
            boolean canExecute = myExternalProfile != null && myExternalProfile.isExecutable(project);
            dialog.setOKActionEnabled(canExecute);
            if (canExecute) {
                manager.setProfile(myExternalProfile.getName());
            }
        });

        reloadProfiles(profiles, project);

        return panel.myAdditionalPanel;
    }

    protected CodeInspectionAction.ExternalProfilesComboboxAwareInspectionToolsConfigurable createConfigurable(
            ProjectInspectionProfileManager projectProfileManager,
            SchemesCombo<InspectionProfileImpl> profilesCombo
    ) {
        return new CodeInspectionAction.ExternalProfilesComboboxAwareInspectionToolsConfigurable(projectProfileManager, profilesCombo);
    }

    protected static class ExternalProfilesComboboxAwareInspectionToolsConfigurable extends InspectionToolsConfigurable {
        private final SchemesCombo<InspectionProfileImpl> myProfilesCombo;
        private String mySelectedName;
        private boolean mySelectedIsProjectProfile;

        public ExternalProfilesComboboxAwareInspectionToolsConfigurable(
                @NotNull ProjectInspectionProfileManager projectProfileManager,
                SchemesCombo<InspectionProfileImpl> profilesCombo
        ) {
            super(projectProfileManager);
            myProfilesCombo = profilesCombo;
        }

        @Override
        protected InspectionProfileImpl getCurrentProfile() {
            return myProfilesCombo.getSelectedScheme();
        }

        @Override
        protected void applyRootProfile(@NotNull String name, boolean isProjectLevel) {
            mySelectedName = name;
            mySelectedIsProjectProfile = isProjectLevel;
        }
    }

    private static void reloadProfiles(SchemesCombo<InspectionProfileImpl> profilesCombo, Project project) {
        InspectionManagerEx managerEx = (InspectionManagerEx) InspectionManager.getInstance(project);
        InspectionProfileImpl migrationProfile = createMigrationProfile(managerEx, null);

        profilesCombo.resetSchemes(Collections.singletonList(migrationProfile));
        profilesCombo.selectScheme(migrationProfile);
    }

    private static class AdditionalPanel {
        public ComboboxWithBrowseButton myBrowseProfilesCombo;
        public JPanel myAdditionalPanel;

        private void createUIComponents() {
            myBrowseProfilesCombo = new ComboboxWithBrowseButton(new SchemesCombo<InspectionProfileImpl>() {
                @Override
                protected boolean supportsProjectSchemes() {
                    return true;
                }

                @Override
                protected boolean isProjectScheme(@NotNull InspectionProfileImpl profile) {
                    return profile.isProjectLevel();
                }

                @NotNull
                @Override
                protected SimpleTextAttributes getSchemeAttributes(InspectionProfileImpl profile) {
                    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
                }
            });
        }
    }

    private static class MySingleConfigurableEditor extends SingleConfigurableEditor {
        private final InspectionManagerEx myManager;

        protected MySingleConfigurableEditor(Project project, ErrorsConfigurable configurable, InspectionManagerEx manager) {
            super(project, configurable, createDimensionKey(configurable));
            myManager = manager;
        }


        @Override
        protected void doOKAction() {
            Object o = ((ErrorsConfigurable) getConfigurable()).getSelectedObject();
            if (o instanceof InspectionProfile) {
                myManager.setProfile(((InspectionProfile) o).getName());
            }
            super.doOKAction();
        }
    }
}